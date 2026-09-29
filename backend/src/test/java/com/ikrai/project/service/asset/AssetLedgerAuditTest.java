package com.ikrai.project.service.asset;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.ikrai.project.common.AuthUser;
import com.ikrai.project.common.enums.AssetStatus;
import com.ikrai.project.common.enums.UserRole;
import com.ikrai.project.common.exception.BusinessException;
import com.ikrai.project.dao.AssetCategoryDao;
import com.ikrai.project.dao.AssetDao;
import com.ikrai.project.dao.AuditLogDao;
import com.ikrai.project.dao.SysUserDao;
import com.ikrai.project.dataobject.AssetDO;
import com.ikrai.project.dataobject.AuditLogDO;
import com.ikrai.project.dataobject.SysUserDO;
import com.ikrai.project.dto.AssetSaveDTO;
import com.ikrai.project.dto.BorrowCreateDTO;
import com.ikrai.project.service.borrow.BorrowService;
import com.ikrai.project.vo.AssetVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AssetLedgerAuditTest {

    private static final AtomicInteger SEQ = new AtomicInteger(1);

    @Autowired
    private AssetService assetService;
    @Autowired
    private BorrowService borrowService;
    @Autowired
    private AssetDao assetDao;
    @Autowired
    private AuditLogDao auditLogDao;
    @Autowired
    private AssetCategoryDao assetCategoryDao;
    @Autowired
    private SysUserDao sysUserDao;
    @Autowired
    private PasswordEncoder passwordEncoder;

    private AuthUser admin;
    private AuthUser user;
    private Long categoryId;

    @BeforeEach
    void setUp() {
        categoryId = assetCategoryDao.selectById(1L).getId();
        admin = asUser(insertUser("ledger_admin", UserRole.ADMIN));
        user = asUser(insertUser("ledger_user", UserRole.USER));
    }

    @Test
    void createWritesAuditAndKeepsAssetInStock() {
        String no = next("LED-NEW");
        AssetVO saved = assetService.save(admin, asset(no, "审计笔记本"));

        AssetDO asset = assetDao.selectById(saved.getId());
        assertEquals(AssetStatus.IN_STOCK.name(), asset.getStatus());
        List<AuditLogDO> logs = logs(saved.getAssetNo(), "CREATE");
        assertEquals(1, logs.size());
        assertEquals("ASSET", logs.get(0).getModule());
        assertEquals(admin.getUserId(), logs.get(0).getOperatorId());
        assertTrue(logs.get(0).getSummary().contains("新增设备 " + saved.getAssetNo()));
        assertTrue(logs.get(0).getSummary().contains("审计笔记本"));
    }

    @Test
    void saveWithoutOperatorWritesNoCreateAudit() {
        AssetVO saved = assetService.save(asset(next("LED-IMP"), "导入式新增"));

        assertEquals(AssetStatus.IN_STOCK.name(), assetDao.selectById(saved.getId()).getStatus());
        assertEquals(0, logs(saved.getAssetNo(), "CREATE").size());
    }

    @Test
    void duplicateAssetNoWritesNothingAndLeavesFirstAsset() {
        String no = next("LED-DUP");
        AssetVO first = assetService.save(admin, asset(no, "第一台"));
        AssetSaveDTO second = asset(no, "第二台");

        assertThrows(BusinessException.class, () -> assetService.save(admin, second));

        assertEquals("第一台", assetDao.selectById(first.getId()).getName());
        assertEquals(AssetStatus.IN_STOCK.name(), assetDao.selectById(first.getId()).getStatus());
        assertEquals(1, logs(no, "CREATE").size());
    }

    @Test
    void updateWritesChangedFieldsAndKeepsStatus() {
        String no = next("LED-UPD");
        AssetVO saved = assetService.save(admin, asset(no, "旧名称"));
        AssetSaveDTO dto = asset(no, "新名称");
        dto.setPurchasePrice(new BigDecimal("1999.00"));
        dto.setBrand("联想");

        assetService.update(admin, saved.getId(), dto);

        AssetDO asset = assetDao.selectById(saved.getId());
        assertEquals("新名称", asset.getName());
        assertEquals(0, new BigDecimal("1999.00").compareTo(asset.getPurchasePrice()));
        assertEquals(AssetStatus.IN_STOCK.name(), asset.getStatus());
        List<AuditLogDO> logs = logs(no, "UPDATE");
        assertEquals(1, logs.size());
        String summary = logs.get(0).getSummary();
        assertTrue(summary.contains("名称由「旧名称」改为「新名称」"));
        assertTrue(summary.contains("购置价格由「空」改为「1999」"));
        assertTrue(summary.contains("品牌由「空」改为「联想」"));
        assertFalse(summary.contains("Passw0rd"));
    }

    @Test
    void scrappedUpdateWritesNothingAndLeavesAsset() {
        String no = next("LED-SCR");
        AssetVO saved = assetService.save(admin, asset(no, "只读设备"));
        AssetDO asset = assetDao.selectById(saved.getId());
        asset.setStatus(AssetStatus.SCRAPPED.name());
        assetDao.updateById(asset);

        AssetSaveDTO dto = asset(no, "想改名");
        BusinessException ex = assertThrows(BusinessException.class,
                () -> assetService.update(admin, saved.getId(), dto));

        assertTrue(ex.getMessage().contains("已报废"));
        AssetDO after = assetDao.selectById(saved.getId());
        assertEquals("只读设备", after.getName());
        assertEquals(AssetStatus.SCRAPPED.name(), after.getStatus());
        assertEquals(0, logs(no, "UPDATE").size());
    }

    @Test
    void deleteOfBorrowedAssetWritesNothing() {
        String no = next("LED-BOR");
        AssetVO saved = assetService.save(admin, asset(no, "已领用设备"));
        AssetDO asset = assetDao.selectById(saved.getId());
        asset.setStatus(AssetStatus.BORROWED.name());
        assetDao.updateById(asset);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> assetService.remove(admin, saved.getId()));

        assertTrue(ex.getMessage().contains("仅在库"));
        assertEquals(AssetStatus.BORROWED.name(), assetDao.selectById(saved.getId()).getStatus());
        assertEquals(0, logs(no, "DELETE").size());
    }

    @Test
    void deleteWithBorrowHistoryWritesNothingAndKeepsInStock() {
        String no = next("LED-HIS");
        AssetVO saved = assetService.save(admin, asset(no, "有领用记录"));
        BorrowCreateDTO borrow = new BorrowCreateDTO();
        borrow.setAssetId(saved.getId());
        borrow.setPurpose("本人使用");
        borrow.setExpectedReturnDate(LocalDate.now().plusDays(5));
        borrow.setSubmit(true);
        var order = borrowService.save(user, borrow);
        borrowService.withdraw(user, order.getId());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> assetService.remove(admin, saved.getId()));

        assertTrue(ex.getMessage().contains("仅在库"));
        AssetDO after = assetDao.selectById(saved.getId());
        assertEquals(AssetStatus.IN_STOCK.name(), after.getStatus());
        assertEquals("有领用记录", after.getName());
        assertEquals(0, logs(no, "DELETE").size());
    }

    @Test
    void deleteWritesAuditAndRemovesAsset() {
        String no = next("LED-DEL");
        AssetVO saved = assetService.save(admin, asset(no, "待删除设备"));

        assetService.remove(admin, saved.getId());

        assertNull(assetDao.selectById(saved.getId()));
        List<AuditLogDO> logs = logs(no, "DELETE");
        assertEquals(1, logs.size());
        assertTrue(logs.get(0).getSummary().contains("删除设备 " + no));
        assertTrue(logs.get(0).getSummary().contains("待删除设备"));
    }

    private List<AuditLogDO> logs(String assetNo, String action) {
        return auditLogDao.selectList(Wrappers.<AuditLogDO>lambdaQuery()
                .eq(AuditLogDO::getModule, "ASSET")
                .eq(AuditLogDO::getObjectNo, assetNo)
                .eq(AuditLogDO::getAction, action));
    }

    private String next(String prefix) {
        return prefix + "-" + SEQ.getAndIncrement();
    }

    private AssetSaveDTO asset(String assetNo, String name) {
        AssetSaveDTO dto = new AssetSaveDTO();
        dto.setAssetNo(assetNo);
        dto.setName(name);
        dto.setCategoryId(categoryId);
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
