package com.ikrai.project.service.borrow;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.ikrai.project.common.AuthUser;
import com.ikrai.project.common.enums.AssetStatus;
import com.ikrai.project.common.enums.BorrowOrderStatus;
import com.ikrai.project.common.enums.UserRole;
import com.ikrai.project.common.exception.BusinessException;
import com.ikrai.project.dao.AssetCategoryDao;
import com.ikrai.project.dao.AssetDao;
import com.ikrai.project.dao.AuditLogDao;
import com.ikrai.project.dao.SysUserDao;
import com.ikrai.project.dataobject.AuditLogDO;
import com.ikrai.project.dataobject.SysUserDO;
import com.ikrai.project.dto.AssetSaveDTO;
import com.ikrai.project.dto.BorrowCreateDTO;
import com.ikrai.project.dto.RenewApplyDTO;
import com.ikrai.project.service.asset.AssetService;
import com.ikrai.project.vo.AssetVO;
import com.ikrai.project.vo.BorrowOrderVO;
import com.ikrai.project.vo.BorrowRenewVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
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
class BorrowAuditTest {

    private static final AtomicInteger SEQ = new AtomicInteger(1);

    @Autowired
    private BorrowService borrowService;
    @Autowired
    private AssetService assetService;
    @Autowired
    private AssetDao assetDao;
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
        admin = asUser(insertUser("audit_admin", UserRole.ADMIN));
        user = asUser(insertUser("audit_user", UserRole.USER));
    }

    @Test
    void approveIssueAndReturnWriteAuditAndRestoreStock() {
        AssetVO asset = createAsset("AUD-FLOW");
        BorrowOrderVO order = borrowService.save(user, submitDto(asset.getId()));
        borrowService.approve(admin, order.getId());
        assertEquals(AssetStatus.PENDING.name(), assetDao.selectById(asset.getId()).getStatus());

        borrowService.issue(admin, order.getId());
        assertEquals(AssetStatus.BORROWED.name(), assetDao.selectById(asset.getId()).getStatus());

        borrowService.confirmReturn(admin, order.getId(), "外观完好");
        assertEquals(AssetStatus.IN_STOCK.name(), assetDao.selectById(asset.getId()).getStatus());
        assertEquals(BorrowOrderStatus.RETURNED.name(), borrowService.get(admin, order.getId()).getStatus());

        List<AuditLogDO> logs = audits(order.getOrderNo());
        assertEquals(List.of("APPROVE", "ISSUE", "RETURN"), logs.stream().map(AuditLogDO::getAction).toList());
        assertTrue(logs.stream().allMatch(item -> "BORROW".equals(item.getModule())));
        assertTrue(logs.stream().allMatch(item -> admin.getUserId().equals(item.getOperatorId())));
        assertTrue(logs.get(2).getSummary().contains("确认归还"));
        assertTrue(logs.get(2).getSummary().contains(asset.getAssetNo()));
    }

    @Test
    void blankRejectWritesNothingAndReasonedRejectWritesAudit() {
        AssetVO asset = createAsset("AUD-REJ");
        BorrowOrderVO order = borrowService.save(user, submitDto(asset.getId()));

        assertThrows(BusinessException.class, () -> borrowService.reject(admin, order.getId(), "  "));
        assertEquals(0, audits(order.getOrderNo()).size());
        assertEquals(AssetStatus.PENDING.name(), assetDao.selectById(asset.getId()).getStatus());

        borrowService.reject(admin, order.getId(), "暂不发放");
        assertEquals(AssetStatus.IN_STOCK.name(), assetDao.selectById(asset.getId()).getStatus());
        List<AuditLogDO> logs = audits(order.getOrderNo());
        assertEquals(1, logs.size());
        assertEquals("REJECT", logs.get(0).getAction());
        assertTrue(logs.get(0).getSummary().contains("暂不发放"));
    }

    @Test
    void duplicateIssueDoesNotWriteAnotherAudit() {
        AssetVO asset = createAsset("AUD-DUP");
        BorrowOrderVO order = borrowService.save(user, submitDto(asset.getId()));
        borrowService.approve(admin, order.getId());
        borrowService.issue(admin, order.getId());

        assertThrows(Exception.class, () -> borrowService.issue(admin, order.getId()));

        assertEquals(AssetStatus.BORROWED.name(), assetDao.selectById(asset.getId()).getStatus());
        assertEquals(1, audits(order.getOrderNo()).stream().filter(item -> "ISSUE".equals(item.getAction())).count());
    }

    @Test
    void renewApprovalWritesAuditAndKeepsBorrowing() {
        AssetVO asset = createAsset("AUD-RN");
        BorrowOrderVO order = borrowService.save(user, submitDto(asset.getId()));
        borrowService.approve(admin, order.getId());
        borrowService.issue(admin, order.getId());
        LocalDate next = LocalDate.now().plusDays(20);
        BorrowRenewVO renew = borrowService.applyRenew(user, order.getId(), renewDto(next, "课题延期"));

        borrowService.approveRenew(admin, renew.getId());

        assertEquals(BorrowOrderStatus.BORROWING.name(), borrowService.get(admin, order.getId()).getStatus());
        assertEquals(AssetStatus.BORROWED.name(), assetDao.selectById(asset.getId()).getStatus());
        assertEquals(next, assetDao.selectById(asset.getId()).getExpectedReturnDate());
        AuditLogDO log = audits(order.getOrderNo()).stream()
                .filter(item -> item.getSummary() != null && item.getSummary().contains("续借"))
                .findFirst()
                .orElseThrow();
        assertEquals("APPROVE", log.getAction());
        assertTrue(log.getSummary().contains(next.toString()));
    }

    private List<AuditLogDO> audits(String orderNo) {
        return auditLogDao.selectList(Wrappers.<AuditLogDO>lambdaQuery()
                .eq(AuditLogDO::getObjectNo, orderNo)
                .orderByAsc(AuditLogDO::getId));
    }

    private RenewApplyDTO renewDto(LocalDate date, String reason) {
        RenewApplyDTO dto = new RenewApplyDTO();
        dto.setNewReturnDate(date);
        dto.setReason(reason);
        return dto;
    }

    private AssetVO createAsset(String assetNo) {
        AssetSaveDTO dto = new AssetSaveDTO();
        dto.setAssetNo(assetNo + "-" + SEQ.getAndIncrement());
        dto.setName("审计笔记本");
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
