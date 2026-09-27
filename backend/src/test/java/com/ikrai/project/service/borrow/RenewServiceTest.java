package com.ikrai.project.service.borrow;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.ikrai.project.common.AuthUser;
import com.ikrai.project.common.enums.AssetStatus;
import com.ikrai.project.common.enums.BorrowOrderStatus;
import com.ikrai.project.common.enums.MailStatus;
import com.ikrai.project.common.enums.MessageType;
import com.ikrai.project.common.enums.RenewStatus;
import com.ikrai.project.common.enums.UserRole;
import com.ikrai.project.common.exception.BusinessException;
import com.ikrai.project.common.exception.ConflictException;
import com.ikrai.project.dao.AssetCategoryDao;
import com.ikrai.project.dao.AssetDao;
import com.ikrai.project.dao.AuditLogDao;
import com.ikrai.project.dao.BorrowOrderDao;
import com.ikrai.project.dao.MailRecordDao;
import com.ikrai.project.dao.SiteMessageDao;
import com.ikrai.project.dao.SysUserDao;
import com.ikrai.project.dataobject.AssetDO;
import com.ikrai.project.dataobject.AuditLogDO;
import com.ikrai.project.dataobject.BorrowOrderDO;
import com.ikrai.project.dataobject.MailRecordDO;
import com.ikrai.project.dataobject.SiteMessageDO;
import com.ikrai.project.dataobject.SysUserDO;
import com.ikrai.project.dto.AssetSaveDTO;
import com.ikrai.project.dto.BorrowCreateDTO;
import com.ikrai.project.dto.RenewApplyDTO;
import com.ikrai.project.dto.SysParamUpdateDTO;
import com.ikrai.project.manager.mail.MailManager;
import com.ikrai.project.manager.user.LoginLockManager;
import com.ikrai.project.service.asset.AssetService;
import com.ikrai.project.service.reminder.ReminderService;
import com.ikrai.project.service.system.SysParamService;
import com.ikrai.project.vo.AssetVO;
import com.ikrai.project.vo.BorrowOrderVO;
import com.ikrai.project.vo.BorrowRenewVO;
import com.ikrai.project.vo.SysParamVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class RenewServiceTest {

    private static final AtomicInteger SEQ = new AtomicInteger(1);

    @Autowired
    private BorrowService borrowService;
    @Autowired
    private AssetService assetService;
    @Autowired
    private SysParamService sysParamService;
    @Autowired
    private ReminderService reminderService;
    @Autowired
    private MailManager mailManager;
    @Autowired
    private LoginLockManager loginLockManager;
    @Autowired
    private AssetDao assetDao;
    @Autowired
    private BorrowOrderDao borrowOrderDao;
    @Autowired
    private SiteMessageDao siteMessageDao;
    @Autowired
    private MailRecordDao mailRecordDao;
    @Autowired
    private AuditLogDao auditLogDao;
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
        admin = asUser(insertUser("renew_admin", UserRole.ADMIN));
        user = asUser(insertUser("renew_user", UserRole.USER));
    }

    @Test
    void renewFailsWhenOrderIsNotBorrowingAndAssetStaysPending() {
        AssetVO asset = createAsset("RN-PEND");
        BorrowOrderVO order = borrowService.save(user, submitDto(asset.getId()));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> borrowService.applyRenew(user, order.getId(), renewDto(LocalDate.now().plusDays(20), "延期")));

        assertTrue(ex.getMessage().contains("待审批"));
        assertEquals(BorrowOrderStatus.PENDING.name(), borrowService.get(admin, order.getId()).getStatus());
        assertEquals(AssetStatus.PENDING.name(), assetDao.selectById(asset.getId()).getStatus());
    }

    @Test
    void renewFailsWhenNewDateIsNotAfterOldDate() {
        BorrowOrderVO order = issued("RN-DATE");
        LocalDate oldDate = order.getExpectedReturnDate();

        BusinessException ex = assertThrows(BusinessException.class,
                () -> borrowService.applyRenew(user, order.getId(), renewDto(oldDate, "同一天")));

        assertTrue(ex.getMessage().contains("晚于"));
        assertSameDue(order.getId(), order.getAssetId(), oldDate);
    }

    @Test
    void renewFailsWhenBeyondIssueCapAndDatesStay() {
        BorrowOrderVO order = issued("RN-CAP");
        LocalDate tooLate = order.getIssuedAt().toLocalDate().plusDays(181);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> borrowService.applyRenew(user, order.getId(), renewDto(tooLate, "太长")));

        assertTrue(ex.getMessage().contains("180"));
        assertSameDue(order.getId(), order.getAssetId(), order.getExpectedReturnDate());
        assertEquals(AssetStatus.BORROWED.name(), assetDao.selectById(order.getAssetId()).getStatus());
    }

    @Test
    void secondRenewWhilePendingFails() {
        BorrowOrderVO order = issued("RN-DUP");
        borrowService.applyRenew(user, order.getId(), renewDto(LocalDate.now().plusDays(20), "第一次"));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> borrowService.applyRenew(user, order.getId(), renewDto(LocalDate.now().plusDays(21), "第二次")));

        assertTrue(ex.getMessage().contains("待审批的续借"));
        assertSameDue(order.getId(), order.getAssetId(), order.getExpectedReturnDate());
        assertEquals(BorrowOrderStatus.BORROWING.name(), borrowService.get(admin, order.getId()).getStatus());
    }

    @Test
    void approveKeepsBorrowingAndUpdatesBothDates() {
        BorrowOrderVO order = issued("RN-OK");
        LocalDate next = LocalDate.now().plusDays(40);
        BorrowRenewVO renew = borrowService.applyRenew(user, order.getId(), renewDto(next, "课题延期"));

        BorrowRenewVO approved = borrowService.approveRenew(admin, renew.getId());

        assertEquals(RenewStatus.APPROVED.name(), approved.getStatus());
        assertEquals(BorrowOrderStatus.BORROWING.name(), borrowService.get(admin, order.getId()).getStatus());
        assertEquals(AssetStatus.BORROWED.name(), assetDao.selectById(order.getAssetId()).getStatus());
        assertEquals(next, borrowService.get(admin, order.getId()).getExpectedReturnDate());
        assertEquals(next, assetDao.selectById(order.getAssetId()).getExpectedReturnDate());
        assertTrue(hasMessage(user.getUserId(), MessageType.RENEW_RESULT, "续借已通过"));
        assertTrue(hasMessage(admin.getUserId(), MessageType.RENEW_RESULT, "续借已通过"));
    }

    @Test
    void rejectWithoutReasonFailsAndDatesStay() {
        BorrowOrderVO order = issued("RN-RJ0");
        BorrowRenewVO renew = borrowService.applyRenew(user, order.getId(), renewDto(LocalDate.now().plusDays(20), "延期"));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> borrowService.rejectRenew(admin, renew.getId(), "  "));

        assertTrue(ex.getMessage().contains("原因"));
        assertEquals(RenewStatus.PENDING.name(), borrowService.listTodos().getRenewPending().get(0).getStatus());
        assertSameDue(order.getId(), order.getAssetId(), order.getExpectedReturnDate());
    }

    @Test
    void rejectKeepsOriginalDateAndAllowsAnotherRenew() {
        BorrowOrderVO order = issued("RN-RJ");
        BorrowRenewVO renew = borrowService.applyRenew(user, order.getId(), renewDto(LocalDate.now().plusDays(20), "延期"));

        BorrowRenewVO rejected = borrowService.rejectRenew(admin, renew.getId(), "暂不延长");

        assertEquals(RenewStatus.REJECTED.name(), rejected.getStatus());
        assertSameDue(order.getId(), order.getAssetId(), order.getExpectedReturnDate());
        assertEquals(AssetStatus.BORROWED.name(), assetDao.selectById(order.getAssetId()).getStatus());
        assertTrue(hasMessage(user.getUserId(), MessageType.RENEW_RESULT, "暂不延长"));
        BorrowRenewVO again = borrowService.applyRenew(user, order.getId(), renewDto(LocalDate.now().plusDays(25), "再次申请"));
        assertEquals(RenewStatus.PENDING.name(), again.getStatus());
    }

    @Test
    void duplicateApproveReturnsConflictAndKeepsNewDate() {
        BorrowOrderVO order = issued("RN-409");
        LocalDate next = LocalDate.now().plusDays(30);
        BorrowRenewVO renew = borrowService.applyRenew(user, order.getId(), renewDto(next, "延期"));
        borrowService.approveRenew(admin, renew.getId());

        ConflictException ex = assertThrows(ConflictException.class,
                () -> borrowService.approveRenew(admin, renew.getId()));

        assertEquals("状态已变化，请刷新", ex.getMessage());
        assertEquals(next, borrowService.get(admin, order.getId()).getExpectedReturnDate());
        assertEquals(next, assetDao.selectById(order.getAssetId()).getExpectedReturnDate());
        assertEquals(BorrowOrderStatus.BORROWING.name(), borrowService.get(admin, order.getId()).getStatus());
    }

    @Test
    void returnWhileRenewPendingFails() {
        BorrowOrderVO order = issued("RN-RET");
        borrowService.applyRenew(user, order.getId(), renewDto(LocalDate.now().plusDays(20), "延期"));

        BusinessException ex = assertThrows(BusinessException.class, () -> borrowService.requestReturn(user, order.getId()));

        assertTrue(ex.getMessage().contains("续借"));
        assertEquals(BorrowOrderStatus.BORROWING.name(), borrowService.get(admin, order.getId()).getStatus());
        assertEquals(AssetStatus.BORROWED.name(), assetDao.selectById(order.getAssetId()).getStatus());
    }

    @Test
    void userCannotApproveRenew() {
        BorrowOrderVO order = issued("RN-403");
        BorrowRenewVO renew = borrowService.applyRenew(user, order.getId(), renewDto(LocalDate.now().plusDays(20), "延期"));

        BusinessException ex = assertThrows(BusinessException.class, () -> borrowService.approveRenew(user, renew.getId()));

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
        assertSameDue(order.getId(), order.getAssetId(), order.getExpectedReturnDate());
    }

    @Test
    void shorterBorrowLimitAppliesToNextApplyOnly() {
        BorrowOrderVO existing = issued("RN-OLD");
        LocalDate kept = existing.getExpectedReturnDate();
        SysParamUpdateDTO dto = currentParams();
        dto.setBorrowMaxDays(10);
        sysParamService.update(admin, dto);

        AssetVO asset = createAsset("RN-NEW");
        BorrowCreateDTO create = submitDto(asset.getId());
        create.setExpectedReturnDate(LocalDate.now().plusDays(11));
        BusinessException ex = assertThrows(BusinessException.class, () -> borrowService.save(user, create));

        assertTrue(ex.getMessage().contains("10"));
        assertEquals(AssetStatus.IN_STOCK.name(), assetDao.selectById(asset.getId()).getStatus());
        assertEquals(kept, borrowService.get(admin, existing.getId()).getExpectedReturnDate());
        assertTrue(auditLogDao.selectCount(Wrappers.<AuditLogDO>lambdaQuery()
                .eq(AuditLogDO::getModule, "PARAM")
                .eq(AuditLogDO::getOperatorId, admin.getUserId())) > 0);
    }

    @Test
    void renewCapFollowsParam() {
        SysParamUpdateDTO dto = currentParams();
        dto.setRenewMaxDaysFromIssue(20);
        sysParamService.update(admin, dto);
        BorrowOrderVO order = issued("RN-P20");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> borrowService.applyRenew(user, order.getId(), renewDto(LocalDate.now().plusDays(21), "超限")));

        assertTrue(ex.getMessage().contains("20"));
        assertSameDue(order.getId(), order.getAssetId(), order.getExpectedReturnDate());
    }

    @Test
    void reminderUsesConfiguredLeadDays() {
        SysParamUpdateDTO dto = currentParams();
        dto.setRemindLeadDays("2");
        sysParamService.update(admin, dto);
        BorrowOrderVO order = issued("RN-REM");
        LocalDate today = LocalDate.now();
        reminderService.scan(today);
        assertEquals(0, messageCount(user.getUserId(), MessageType.DUE_REMIND));

        BorrowOrderDO stored = borrowOrderDao.selectById(order.getId());
        stored.setExpectedReturnDate(today.plusDays(2));
        borrowOrderDao.updateById(stored);
        AssetDO asset = assetDao.selectById(order.getAssetId());
        asset.setExpectedReturnDate(today.plusDays(2));
        assetDao.updateById(asset);

        reminderService.scan(today);
        assertTrue(messageCount(user.getUserId(), MessageType.DUE_REMIND) > 0);
    }

    @Test
    void closedMailChannelSkipsWithClosedReason() {
        SysParamUpdateDTO dto = currentParams();
        dto.setMailChannelEnabled(false);
        sysParamService.update(admin, dto);

        MailRecordDO record = mailManager.create(user.getUserId(), "renew@example.com", "续借", "正文", null);

        assertEquals(MailStatus.SKIPPED.name(), record.getStatus());
        assertEquals("邮件通道已关闭", mailRecordDao.selectById(record.getId()).getFailReason());
    }

    @Test
    void loginLockUsesParam() {
        SysParamUpdateDTO dto = currentParams();
        dto.setLoginMaxFailures(2);
        sysParamService.update(admin, dto);
        String username = "lock-" + SEQ.getAndIncrement();
        loginLockManager.recordFailure(username);
        loginLockManager.recordFailure(username);

        BusinessException ex = assertThrows(BusinessException.class, () -> loginLockManager.assertUnlocked(username));
        assertTrue(ex.getMessage().contains("锁定"));
    }

    private void assertSameDue(Long orderId, Long assetId, LocalDate expected) {
        assertEquals(expected, borrowService.get(admin, orderId).getExpectedReturnDate());
        assertEquals(expected, assetDao.selectById(assetId).getExpectedReturnDate());
        assertEquals(BorrowOrderStatus.BORROWING.name(), borrowService.get(admin, orderId).getStatus());
        assertEquals(AssetStatus.BORROWED.name(), assetDao.selectById(assetId).getStatus());
    }

    private boolean hasMessage(Long receiverId, MessageType type, String snippet) {
        return siteMessageDao.selectList(Wrappers.<SiteMessageDO>lambdaQuery()
                        .eq(SiteMessageDO::getReceiverId, receiverId)
                        .eq(SiteMessageDO::getMsgType, type.name()))
                .stream()
                .anyMatch(item -> item.getContent() != null && item.getContent().contains(snippet)
                        || item.getTitle() != null && item.getTitle().contains(snippet));
    }

    private long messageCount(Long receiverId, MessageType type) {
        Long count = siteMessageDao.selectCount(Wrappers.<SiteMessageDO>lambdaQuery()
                .eq(SiteMessageDO::getReceiverId, receiverId)
                .eq(SiteMessageDO::getMsgType, type.name()));
        return count == null ? 0 : count;
    }

    private SysParamUpdateDTO currentParams() {
        SysParamVO vo = sysParamService.get();
        SysParamUpdateDTO dto = new SysParamUpdateDTO();
        dto.setRemindLeadDays(vo.getRemindLeadDays());
        dto.setBorrowMaxDays(vo.getBorrowMaxDays());
        dto.setRenewMaxDaysFromIssue(vo.getRenewMaxDaysFromIssue());
        dto.setMailChannelEnabled(vo.isMailChannelEnabled());
        dto.setLoginMaxFailures(vo.getLoginMaxFailures());
        dto.setLoginLockMinutes(vo.getLoginLockMinutes());
        return dto;
    }

    private RenewApplyDTO renewDto(LocalDate date, String reason) {
        RenewApplyDTO dto = new RenewApplyDTO();
        dto.setNewReturnDate(date);
        dto.setReason(reason);
        return dto;
    }

    private BorrowOrderVO issued(String assetNo) {
        AssetVO asset = createAsset(assetNo);
        BorrowOrderVO order = borrowService.save(user, submitDto(asset.getId()));
        borrowService.approve(admin, order.getId());
        return borrowService.issue(admin, order.getId());
    }

    private AssetVO createAsset(String assetNo) {
        AssetSaveDTO dto = new AssetSaveDTO();
        dto.setAssetNo(assetNo + "-" + SEQ.getAndIncrement());
        dto.setName("测试笔记本");
        dto.setCategoryId(categoryId);
        return assetService.save(dto);
    }

    private BorrowCreateDTO submitDto(Long assetId) {
        BorrowCreateDTO dto = new BorrowCreateDTO();
        dto.setAssetId(assetId);
        dto.setPurpose("课题演示");
        dto.setExpectedReturnDate(LocalDate.now().plusDays(7));
        dto.setSubmit(true);
        return dto;
    }

    private SysUserDO insertUser(String username, UserRole role) {
        SysUserDO row = new SysUserDO();
        row.setUsername(username + SEQ.getAndIncrement());
        row.setPasswordHash(passwordEncoder.encode("Passw0rd"));
        row.setRealName(username);
        row.setEmail(username + "@example.com");
        row.setRole(role.name());
        row.setEnabled(1);
        row.setTokenVersion(0);
        row.setMustChangePassword(0);
        sysUserDao.insert(row);
        return row;
    }

    private AuthUser asUser(SysUserDO row) {
        return new AuthUser(row.getId(), row.getUsername(), row.getRole(), row.getTokenVersion());
    }
}
