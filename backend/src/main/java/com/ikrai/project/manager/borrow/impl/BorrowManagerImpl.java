package com.ikrai.project.manager.borrow.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.ikrai.project.common.enums.BorrowLogAction;
import com.ikrai.project.common.enums.BorrowOrderStatus;
import com.ikrai.project.common.exception.ConflictException;
import com.ikrai.project.common.exception.NotFoundException;
import com.ikrai.project.dao.BorrowLogDao;
import com.ikrai.project.dao.BorrowOrderDao;
import com.ikrai.project.dataobject.BorrowLogDO;
import com.ikrai.project.dataobject.BorrowOrderDO;
import com.ikrai.project.manager.borrow.BorrowManager;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Component
public class BorrowManagerImpl implements BorrowManager {

    private static final List<String> ACTIVE_STATUSES = List.of(
            BorrowOrderStatus.DRAFT.name(),
            BorrowOrderStatus.PENDING.name(),
            BorrowOrderStatus.APPROVED.name(),
            BorrowOrderStatus.BORROWING.name(),
            BorrowOrderStatus.RETURN_PENDING.name()
    );

    private final BorrowOrderDao borrowOrderDao;
    private final BorrowLogDao borrowLogDao;

    public BorrowManagerImpl(BorrowOrderDao borrowOrderDao, BorrowLogDao borrowLogDao) {
        this.borrowOrderDao = borrowOrderDao;
        this.borrowLogDao = borrowLogDao;
    }

    @Override
    public String nextOrderNo() {
        String prefix = "LY" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        long count = borrowOrderDao.countByOrderNoPrefix(prefix);
        return prefix + String.format("%04d", count + 1);
    }

    @Override
    public BorrowOrderDO getById(Long id) {
        BorrowOrderDO order = borrowOrderDao.selectById(id);
        if (order == null) {
            throw new NotFoundException("领用单不存在");
        }
        return order;
    }

    @Override
    public boolean hasActiveOrder(Long assetId, Long excludeOrderId) {
        Long count = borrowOrderDao.selectCount(Wrappers.<BorrowOrderDO>lambdaQuery()
                .eq(BorrowOrderDO::getAssetId, assetId)
                .in(BorrowOrderDO::getStatus, ACTIVE_STATUSES)
                .ne(excludeOrderId != null, BorrowOrderDO::getId, excludeOrderId));
        return count != null && count > 0;
    }

    @Override
    public void saveLog(Long borrowId, BorrowLogAction action, Long operatorId, String comment) {
        BorrowLogDO log = new BorrowLogDO();
        log.setBorrowId(borrowId);
        log.setAction(action.name());
        log.setOperatorId(operatorId);
        log.setComment(comment);
        borrowLogDao.insert(log);
    }

    @Override
    public void casUpdateStatus(BorrowOrderDO order, String newStatus) {
        int rows = borrowOrderDao.casUpdateStatus(order.getId(), order.getVersion(), order.getStatus(), newStatus);
        if (rows == 0) {
            throw new ConflictException();
        }
        order.setStatus(newStatus);
        order.setVersion(order.getVersion() + 1);
    }
}
