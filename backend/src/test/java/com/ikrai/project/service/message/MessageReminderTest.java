package com.ikrai.project.service.message;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.ikrai.project.common.AuthUser;
import com.ikrai.project.common.enums.AssetStatus;
import com.ikrai.project.common.enums.MailStatus;
import com.ikrai.project.common.enums.MessageType;
import com.ikrai.project.common.enums.UserRole;
import com.ikrai.project.common.exception.BusinessException;
import com.ikrai.project.common.exception.NotFoundException;
import com.ikrai.project.dao.AssetCategoryDao;
import com.ikrai.project.dao.AssetDao;
import com.ikrai.project.dao.BorrowOrderDao;
import com.ikrai.project.dao.MailRecordDao;
import com.ikrai.project.dao.SysUserDao;
import com.ikrai.project.dataobject.AssetCategoryDO;
import com.ikrai.project.dataobject.BorrowOrderDO;
import com.ikrai.project.dataobject.MailRecordDO;
import com.ikrai.project.dataobject.SysUserDO;
import com.ikrai.project.dto.AssetSaveDTO;
import com.ikrai.project.dto.BorrowCreateDTO;
import com.ikrai.project.dto.MessageReadDTO;
import com.ikrai.project.query.MailRecordQuery;
import com.ikrai.project.query.MessageQuery;
import com.ikrai.project.service.asset.AssetService;
import com.ikrai.project.service.borrow.BorrowService;
import com.ikrai.project.service.mail.MailRecordService;
import com.ikrai.project.service.reminder.ReminderService;
import com.ikrai.project.vo.AssetVO;
import com.ikrai.project.vo.BorrowOrderVO;
import com.ikrai.project.vo.MailRecordVO;
import com.ikrai.project.vo.SiteMessageVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class MessageReminderTest {

    private static final AtomicInteger SEQ = new AtomicInteger(1);

    @Autowired
    private BorrowService borrowService;
    @Autowired
    private AssetService assetService;
    @Autowired
    private MessageService messageService;
    @Autowired
    private MailRecordService mailRecordService;
    @Autowired
    private ReminderService reminderService;
    @Autowired
    private AssetDao assetDao;
    @Autowired
    private BorrowOrderDao borrowOrderDao;
    @Autowired
    private MailRecordDao mailRecordDao;
    @Autowired
    private SysUserDao sysUserDao;
    @Autowired
    private AssetCategoryDao assetCategoryDao;
    @Autowired
    private PasswordEncoder passwordEncoder;

    private AuthUser admin;
    private AuthUser user;
    private Long categoryId;

    @BeforeEach
    void setUp() {
        categoryId = assetCategoryDao.selectById(1L).getId();
        admin = asUser(insertUser("msg_admin", UserRole.ADMIN, "msg_admin@example.com", 1));
        user = asUser(insertUser("msg_user", UserRole.USER, "msg_user@example.com", 1));
    }

    @Test
    void rejectNotifiesApplicantAndReleasesAsset() {
        AssetVO asset = createAsset("MSG-RJ");
        BorrowOrderVO order = borrowService.save(user, submitDto(asset.getId(), LocalDate.now().plusDays(10)));

        borrowService.reject(admin, order.getId(), "设备已分配");

        assertEquals(AssetStatus.IN_STOCK.name(), assetDao.selectById(asset.getId()).getStatus());
        List<SiteMessageVO> notices = byType(user, MessageType.APPROVAL_RESULT);
        assertEquals(1, notices.size());
        assertEquals("领用申请已驳回", notices.get(0).getTitle());
        assertTrue(notices.get(0).getContent().contains("设备已分配"));
        assertTrue(notices.get(0).getContent().contains(asset.getAssetNo()));
        assertEquals(0, byBorrow(asUser(insertUser("msg_other", UserRole.USER, "o@example.com", 1)), order.getId()).size());
    }

    @Test
    void issueAndReturnNotifyApplicantWhileAssetFollowsStatus() {
        AssetVO asset = createAsset("MSG-IS");
        BorrowOrderVO order = borrowService.save(user, submitDto(asset.getId(), LocalDate.now().plusDays(20)));
        borrowService.approve(admin, order.getId());
        assertEquals(1, byType(user, MessageType.APPROVAL_RESULT).size());
        assertEquals(AssetStatus.PENDING.name(), assetDao.selectById(asset.getId()).getStatus());

        borrowService.issue(admin, order.getId());
        assertEquals(AssetStatus.BORROWED.name(), assetDao.selectById(asset.getId()).getStatus());
        SiteMessageVO issued = byType(user, MessageType.ISSUE).get(0);
        assertTrue(issued.getContent().contains(asset.getAssetNo()));

        borrowService.confirmReturn(admin, order.getId(), "外观完好");
        assertEquals(AssetStatus.IN_STOCK.name(), assetDao.selectById(asset.getId()).getStatus());
        assertEquals("归还已确认", byType(user, MessageType.RETURN_CONFIRM).get(0).getTitle());
    }

    @Test
    void submitNotifiesEnabledAdminsAndDraftDoesNot() {
        SysUserDO disabled = insertUser("msg_off", UserRole.ADMIN, "off@example.com", 0);
        AssetVO asset = createAsset("MSG-DR");
        BorrowCreateDTO draft = submitDto(asset.getId(), LocalDate.now().plusDays(5));
        draft.setSubmit(false);
        BorrowOrderVO saved = borrowService.save(user, draft);
        assertEquals(0, byBorrow(admin, saved.getId()).size());

        BorrowOrderVO pending = borrowService.submit(user, saved.getId());
        List<SiteMessageVO> adminNotices = byBorrow(admin, pending.getId());
        assertEquals(1, adminNotices.size());
        assertEquals(MessageType.PENDING_APPROVAL.name(), adminNotices.get(0).getMsgType());
        assertTrue(adminNotices.get(0).getContent().contains(pending.getOrderNo()));
        assertEquals(0, byBorrow(asUser(disabled), pending.getId()).size());
        assertEquals(0, byBorrow(user, pending.getId()).size());
    }

    @Test
    void userMarksOwnMessageReadAndCannotMarkOthers() {
        AssetVO asset = createAsset("MSG-RD");
        BorrowOrderVO order = borrowService.save(user, submitDto(asset.getId(), LocalDate.now().plusDays(8)));
        borrowService.reject(admin, order.getId(), "暂不发放");
        SiteMessageVO notice = byType(user, MessageType.APPROVAL_RESULT).get(0);
        long beforeAdmin = messageService.unreadCount(admin);

        assertTrue(messageService.unreadCount(user) >= 1);
        MessageReadDTO read = new MessageReadDTO();
        read.setIds(List.of(notice.getId()));
        messageService.markRead(user, read);
        assertEquals(0, messageService.unreadCount(user));
        assertTrue(byType(user, MessageType.APPROVAL_RESULT).get(0).getRead());

        assertThrows(NotFoundException.class, () -> messageService.markRead(user, readAdminMessage(order.getId())));
        assertEquals(beforeAdmin, messageService.unreadCount(admin));

        MessageReadDTO all = new MessageReadDTO();
        all.setAll(true);
        messageService.markRead(user, all);
        assertEquals(beforeAdmin, messageService.unreadCount(admin));
    }

    @Test
    void dueSoonSendsOneAdminSummaryAndSkippedMailWithoutDuplicate() {
        LocalDate today = LocalDate.now();
        String firstNo = issued("MSG-D7", today.plusDays(7)).assetNo;
        String secondNo = issued("MSG-D3", today.plusDays(3)).assetNo;

        reminderService.scan(today);

        List<SiteMessageVO> userNotices = byType(user, MessageType.DUE_REMIND);
        assertEquals(2, userNotices.size());
        assertTrue(userNotices.stream().allMatch(item -> "您的设备即将到期".equals(item.getTitle())));
        assertTrue(userNotices.stream().anyMatch(item -> item.getContent().contains(firstNo)));
        assertTrue(userNotices.stream().anyMatch(item -> item.getContent().contains(secondNo)));

        List<SiteMessageVO> adminNotices = byType(admin, MessageType.DUE_REMIND);
        assertEquals(1, adminNotices.size());
        assertEquals("即将到期设备汇总", adminNotices.get(0).getTitle());
        assertTrue(adminNotices.get(0).getContent().contains(firstNo));
        assertTrue(adminNotices.get(0).getContent().contains(secondNo));
        assertTrue(adminNotices.get(0).getContent().startsWith("资产编号\t资产名称\t领用人\t预计归还日\t逾期天数"));
        assertEquals(0, mails(admin.getUserId()).size());

        List<MailRecordDO> records = mails(user.getUserId());
        assertEquals(2, records.size());
        assertTrue(records.stream().allMatch(item -> MailStatus.SKIPPED.name().equals(item.getStatus())));
        assertTrue(records.stream().allMatch(item -> "邮件通道未配置".equals(item.getFailReason())));
        assertTrue(records.stream().allMatch(item -> item.getContent().contains(firstNo) || item.getContent().contains(secondNo)));
        assertTrue(records.stream().noneMatch(item -> item.getContent().contains(firstNo) && item.getContent().contains(secondNo)));

        reminderService.scan(today);
        assertEquals(2, byType(user, MessageType.DUE_REMIND).size());
        assertEquals(1, byType(admin, MessageType.DUE_REMIND).size());
        assertEquals(2, mails(user.getUserId()).size());
    }

    @Test
    void sixDaysAheadAndPendingOrderDoNotRemind() {
        LocalDate today = LocalDate.now();
        issued("MSG-D6", today.plusDays(6));
        AssetVO pendingAsset = createAsset("MSG-PD");
        borrowService.save(user, submitDto(pendingAsset.getId(), today.plusDays(7)));

        reminderService.scan(today);

        assertEquals(0, byType(user, MessageType.DUE_REMIND).size());
        assertEquals(0, byType(user, MessageType.OVERDUE_REMIND).size());
        assertEquals(0, byType(admin, MessageType.DUE_REMIND).size());
    }

    @Test
    void overdueScanDoesNotBackfillEarlierDueNotice() {
        LocalDate today = LocalDate.now();
        Issued issued = issued("MSG-OD", today.minusDays(2));
        reminderService.scan(today);

        List<SiteMessageVO> userNotices = byType(user, MessageType.OVERDUE_REMIND);
        assertEquals(1, userNotices.size());
        assertEquals("您的设备已逾期", userNotices.get(0).getTitle());
        assertTrue(userNotices.get(0).getContent().contains(issued.assetNo + "\t"));
        assertTrue(userNotices.get(0).getContent().endsWith("\t2"));
        assertEquals(0, byType(user, MessageType.DUE_REMIND).size());
        assertEquals("已逾期设备汇总", byType(admin, MessageType.OVERDUE_REMIND).get(0).getTitle());
        assertEquals(0, mails(admin.getUserId()).size());
    }

    @Test
    void emptyEmailSkipsMailAndKeepsSiteMessage() {
        SysUserDO blank = insertUser("msg_blank", UserRole.USER, null, 1);
        AuthUser borrower = asUser(blank);
        LocalDate today = LocalDate.now();
        Issued issued = issued(borrower, "MSG-EM", today);
        reminderService.scan(today);

        assertEquals(1, byType(borrower, MessageType.DUE_REMIND).size());
        List<MailRecordDO> records = mails(borrower.getUserId());
        assertEquals(1, records.size());
        assertEquals(MailStatus.SKIPPED.name(), records.get(0).getStatus());
        assertEquals("收件人邮箱为空", records.get(0).getFailReason());
        assertTrue(records.get(0).getContent().contains(issued.assetNo));
    }

    @Test
    void retrySkippedMailStaysSkippedAndDoesNotDuplicateSiteMessage() {
        LocalDate today = LocalDate.now();
        issued("MSG-RT", today.plusDays(1));
        reminderService.scan(today);
        MailRecordDO record = mails(user.getUserId()).get(0);

        MailRecordVO retried = mailRecordService.retry(admin, record.getId());

        assertEquals(MailStatus.SKIPPED.name(), retried.getStatus());
        assertEquals("未发送", retried.getStatusLabel());
        assertEquals("邮件通道未配置", retried.getFailReason());
        assertEquals(1, retried.getRetryCount());
        assertEquals(1, byType(user, MessageType.DUE_REMIND).size());

        BusinessException denied = assertThrows(BusinessException.class,
                () -> mailRecordService.list(user, new MailRecordQuery()));
        assertEquals(HttpStatus.FORBIDDEN, denied.getStatus());
    }

    private MessageReadDTO readAdminMessage(Long borrowId) {
        SiteMessageVO adminNotice = byBorrow(admin, borrowId).get(0);
        MessageReadDTO dto = new MessageReadDTO();
        dto.setIds(List.of(adminNotice.getId()));
        return dto;
    }

    private Issued issued(String assetNo, LocalDate due) {
        return issued(user, assetNo, due);
    }

    private Issued issued(AuthUser applicant, String assetNo, LocalDate due) {
        AssetVO asset = createAsset(assetNo);
        BorrowOrderVO order = borrowService.save(applicant, submitDto(asset.getId(), LocalDate.now().plusDays(30)));
        borrowService.approve(admin, order.getId());
        borrowService.issue(admin, order.getId());
        BorrowOrderDO stored = borrowOrderDao.selectById(order.getId());
        stored.setExpectedReturnDate(due);
        borrowOrderDao.updateById(stored);
        return new Issued(asset.getAssetNo(), order.getId());
    }

    private List<SiteMessageVO> byType(AuthUser viewer, MessageType type) {
        return all(viewer).stream().filter(item -> type.name().equals(item.getMsgType())).toList();
    }

    private List<SiteMessageVO> byBorrow(AuthUser viewer, Long borrowId) {
        return all(viewer).stream().filter(item -> borrowId.equals(item.getBorrowId())).toList();
    }

    private List<SiteMessageVO> all(AuthUser viewer) {
        MessageQuery query = new MessageQuery();
        query.setPage(1);
        query.setPageSize(100);
        return messageService.list(viewer, query).getList();
    }

    private List<MailRecordDO> mails(Long receiverId) {
        return mailRecordDao.selectList(Wrappers.<MailRecordDO>lambdaQuery()
                .eq(MailRecordDO::getReceiverId, receiverId));
    }

    private AssetVO createAsset(String assetNo) {
        AssetSaveDTO dto = new AssetSaveDTO();
        dto.setAssetNo(assetNo + "-" + SEQ.getAndIncrement());
        dto.setName("测试笔记本");
        dto.setCategoryId(categoryId);
        return assetService.save(dto);
    }

    private BorrowCreateDTO submitDto(Long assetId, LocalDate due) {
        BorrowCreateDTO dto = new BorrowCreateDTO();
        dto.setAssetId(assetId);
        dto.setPurpose("课题演示");
        dto.setExpectedReturnDate(due);
        dto.setSubmit(true);
        return dto;
    }

    private SysUserDO insertUser(String username, UserRole role, String email, int enabled) {
        SysUserDO account = new SysUserDO();
        account.setUsername(username + SEQ.getAndIncrement());
        account.setPasswordHash(passwordEncoder.encode("Passw0rd"));
        account.setRealName(username);
        account.setEmail(email);
        account.setRole(role.name());
        account.setEnabled(enabled);
        account.setTokenVersion(0);
        account.setMustChangePassword(0);
        sysUserDao.insert(account);
        return account;
    }

    private AuthUser asUser(SysUserDO account) {
        return new AuthUser(account.getId(), account.getUsername(), account.getRole(), account.getTokenVersion());
    }

    private record Issued(String assetNo, Long orderId) {
    }
}
