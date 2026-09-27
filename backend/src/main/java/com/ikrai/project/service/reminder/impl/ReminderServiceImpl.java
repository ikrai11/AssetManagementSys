package com.ikrai.project.service.reminder.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.ikrai.project.common.enums.BorrowOrderStatus;
import com.ikrai.project.common.enums.MessageType;
import com.ikrai.project.common.enums.RemindType;
import com.ikrai.project.common.enums.UserRole;
import com.ikrai.project.dao.AssetDao;
import com.ikrai.project.dao.BorrowOrderDao;
import com.ikrai.project.dao.ReminderMarkDao;
import com.ikrai.project.dao.SysUserDao;
import com.ikrai.project.dataobject.AssetDO;
import com.ikrai.project.dataobject.BorrowOrderDO;
import com.ikrai.project.dataobject.ReminderMarkDO;
import com.ikrai.project.dataobject.SysUserDO;
import com.ikrai.project.manager.mail.MailManager;
import com.ikrai.project.manager.message.MessageManager;
import com.ikrai.project.service.reminder.ReminderService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Service
public class ReminderServiceImpl implements ReminderService {

    private final BorrowOrderDao borrowOrderDao;
    private final ReminderMarkDao reminderMarkDao;
    private final AssetDao assetDao;
    private final SysUserDao sysUserDao;
    private final MessageManager messageManager;
    private final MailManager mailManager;

    public ReminderServiceImpl(BorrowOrderDao borrowOrderDao,
                               ReminderMarkDao reminderMarkDao,
                               AssetDao assetDao,
                               SysUserDao sysUserDao,
                               MessageManager messageManager,
                               MailManager mailManager) {
        this.borrowOrderDao = borrowOrderDao;
        this.reminderMarkDao = reminderMarkDao;
        this.assetDao = assetDao;
        this.sysUserDao = sysUserDao;
        this.messageManager = messageManager;
        this.mailManager = mailManager;
    }

    @Override
    @Transactional
    public void scan(LocalDate today) {
        List<BorrowOrderDO> orders = borrowOrderDao.selectList(Wrappers.<BorrowOrderDO>lambdaQuery()
                .eq(BorrowOrderDO::getStatus, BorrowOrderStatus.BORROWING.name())
                .isNotNull(BorrowOrderDO::getExpectedReturnDate));
        Map<RemindType, List<BorrowOrderDO>> groups = new EnumMap<>(RemindType.class);
        for (RemindType type : RemindType.values()) {
            groups.put(type, new ArrayList<>());
        }
        for (BorrowOrderDO order : orders) {
            long days = ChronoUnit.DAYS.between(today, order.getExpectedReturnDate());
            if (days == 7 || days == 3 || days == 1) {
                groups.get(RemindType.DUE_SOON).add(order);
            } else if (days == 0) {
                groups.get(RemindType.DUE_TODAY).add(order);
            } else if (days < 0) {
                groups.get(RemindType.OVERDUE).add(order);
            }
        }
        List<SysUserDO> admins = sysUserDao.selectList(Wrappers.<SysUserDO>lambdaQuery()
                .eq(SysUserDO::getRole, UserRole.ADMIN.name())
                .eq(SysUserDO::getEnabled, 1));
        for (Map.Entry<RemindType, List<BorrowOrderDO>> entry : groups.entrySet()) {
            if (entry.getValue().isEmpty()) {
                continue;
            }
            dispatch(today, entry.getKey(), entry.getValue(), admins);
        }
    }

    private void dispatch(LocalDate today, RemindType remindType, List<BorrowOrderDO> orders, List<SysUserDO> admins) {
        MessageType messageType = remindType == RemindType.OVERDUE
                ? MessageType.OVERDUE_REMIND : MessageType.DUE_REMIND;
        String summary = table(orders, today);
        for (SysUserDO admin : admins) {
            if (mark(today, remindType, 0L, admin.getId())) {
                messageManager.send(admin.getId(), messageType, adminTitle(remindType), summary, null);
            }
        }
        for (BorrowOrderDO order : orders) {
            if (!mark(today, remindType, order.getId(), order.getApplicantId())) {
                continue;
            }
            String body = table(List.of(order), today);
            String title = userTitle(remindType);
            messageManager.send(order.getApplicantId(), messageType, title, body, order.getId());
            SysUserDO holder = sysUserDao.selectById(order.getApplicantId());
            mailManager.create(order.getApplicantId(), holder == null ? null : holder.getEmail(),
                    title, body, order.getId());
        }
    }

    private boolean mark(LocalDate bizDate, RemindType remindType, long borrowId, long receiverId) {
        Long existing = reminderMarkDao.selectCount(Wrappers.<ReminderMarkDO>lambdaQuery()
                .eq(ReminderMarkDO::getBizDate, bizDate)
                .eq(ReminderMarkDO::getRemindType, remindType.name())
                .eq(ReminderMarkDO::getBorrowId, borrowId)
                .eq(ReminderMarkDO::getReceiverId, receiverId));
        if (existing != null && existing > 0) {
            return false;
        }
        ReminderMarkDO mark = new ReminderMarkDO();
        mark.setBizDate(bizDate);
        mark.setRemindType(remindType.name());
        mark.setBorrowId(borrowId);
        mark.setReceiverId(receiverId);
        reminderMarkDao.insert(mark);
        return true;
    }

    private String table(List<BorrowOrderDO> orders, LocalDate today) {
        StringBuilder text = new StringBuilder("资产编号\t资产名称\t领用人\t预计归还日\t逾期天数");
        orders.stream()
                .sorted(Comparator.comparing(BorrowOrderDO::getExpectedReturnDate)
                        .thenComparing(BorrowOrderDO::getId))
                .forEach(order -> text.append('\n').append(line(order, today)));
        return text.toString();
    }

    private String line(BorrowOrderDO order, LocalDate today) {
        AssetDO asset = assetDao.selectById(order.getAssetId());
        SysUserDO holder = sysUserDao.selectById(order.getApplicantId());
        int overdueDays = order.getExpectedReturnDate().isBefore(today)
                ? (int) ChronoUnit.DAYS.between(order.getExpectedReturnDate(), today) : 0;
        return (asset == null ? "" : asset.getAssetNo()) + '\t'
                + (asset == null ? "" : asset.getName()) + '\t'
                + (holder == null ? "" : holder.getRealName()) + '\t'
                + order.getExpectedReturnDate() + '\t'
                + overdueDays;
    }

    private String adminTitle(RemindType type) {
        return switch (type) {
            case DUE_SOON -> "即将到期设备汇总";
            case DUE_TODAY -> "今日到期设备汇总";
            case OVERDUE -> "已逾期设备汇总";
        };
    }

    private String userTitle(RemindType type) {
        return switch (type) {
            case DUE_SOON -> "您的设备即将到期";
            case DUE_TODAY -> "您的设备今日到期";
            case OVERDUE -> "您的设备已逾期";
        };
    }
}
