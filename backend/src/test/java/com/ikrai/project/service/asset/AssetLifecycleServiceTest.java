package com.ikrai.project.service.asset;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.ikrai.project.common.AuthUser;
import com.ikrai.project.common.enums.AssetLogAction;
import com.ikrai.project.common.enums.AssetStatus;
import com.ikrai.project.common.enums.UserRole;
import com.ikrai.project.common.exception.BusinessException;
import com.ikrai.project.common.exception.ConflictException;
import com.ikrai.project.common.exception.NotFoundException;
import com.ikrai.project.dao.AssetCategoryDao;
import com.ikrai.project.dao.AssetDao;
import com.ikrai.project.dao.AssetLogDao;
import com.ikrai.project.dao.AuditLogDao;
import com.ikrai.project.dao.SysDeptDao;
import com.ikrai.project.dao.SysLocationDao;
import com.ikrai.project.dao.SysUserDao;
import com.ikrai.project.dataobject.AssetDO;
import com.ikrai.project.dataobject.AssetLogDO;
import com.ikrai.project.dataobject.AuditLogDO;
import com.ikrai.project.dataobject.SysDeptDO;
import com.ikrai.project.dataobject.SysLocationDO;
import com.ikrai.project.dataobject.SysUserDO;
import com.ikrai.project.dto.AssetRepairFinishDTO;
import com.ikrai.project.dto.AssetRepairStartDTO;
import com.ikrai.project.dto.AssetSaveDTO;
import com.ikrai.project.dto.AssetScrapDTO;
import com.ikrai.project.dto.AssetTransferDTO;
import com.ikrai.project.dto.BorrowCreateDTO;
import com.ikrai.project.service.borrow.BorrowService;
import com.ikrai.project.service.stats.StatsService;
import com.ikrai.project.vo.AssetDetailVO;
import com.ikrai.project.vo.AssetVO;
import com.ikrai.project.vo.BorrowOrderVO;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AssetLifecycleServiceTest {

    private static final AtomicInteger SEQ = new AtomicInteger(1);

    @Autowired
    private AssetLifecycleService lifecycleService;
    @Autowired
    private AssetService assetService;
    @Autowired
    private BorrowService borrowService;
    @Autowired
    private StatsService statsService;
    @Autowired
    private AssetDao assetDao;
    @Autowired
    private AssetLogDao assetLogDao;
    @Autowired
    private AuditLogDao auditLogDao;
    @Autowired
    private SysDeptDao sysDeptDao;
    @Autowired
    private SysLocationDao sysLocationDao;
    @Autowired
    private SysUserDao sysUserDao;
    @Autowired
    private AssetCategoryDao assetCategoryDao;
    @Autowired
    private PasswordEncoder passwordEncoder;

    private AuthUser admin;
    private AuthUser user;
    private AuthUser other;
    private Long categoryId;
    private Long deptA;
    private Long deptB;
    private Long locationA;
    private Long locationB;

    @BeforeEach
    void setUp() {
        categoryId = assetCategoryDao.selectById(1L).getId();
        List<SysDeptDO> depts = sysDeptDao.selectList(Wrappers.<SysDeptDO>lambdaQuery().eq(SysDeptDO::getEnabled, 1));
        List<SysLocationDO> locations = sysLocationDao.selectList(Wrappers.<SysLocationDO>lambdaQuery().eq(SysLocationDO::getEnabled, 1));
        deptA = depts.get(0).getId();
        deptB = depts.get(1).getId();
        locationA = locations.get(0).getId();
        locationB = locations.get(1).getId();
        admin = asUser(insertUser("life_admin", UserRole.ADMIN));
        user = asUser(insertUser("life_user", UserRole.USER));
        other = asUser(insertUser("life_other", UserRole.USER));
    }

    @Test
    void transferScrappedAssetFailsAndKeepsDept() {
        AssetVO asset = createAsset("LC-SCRAP-TF", deptA, locationA);
        scrap(asset.getId(), "无法修复");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> lifecycleService.transfer(admin, asset.getId(), transfer(deptB, locationB, "换房间")));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertTrue(ex.getMessage().contains("已报废"));
        AssetDO stored = assetDao.selectById(asset.getId());
        assertEquals(AssetStatus.SCRAPPED.name(), stored.getStatus());
        assertEquals(deptA, stored.getDeptId());
        assertEquals(locationA, stored.getLocationId());
    }

    @Test
    void transferWithoutReasonOrWithoutChangeFails() {
        AssetVO asset = createAsset("LC-TF-BAD", deptA, locationA);

        BusinessException blank = assertThrows(BusinessException.class,
                () -> lifecycleService.transfer(admin, asset.getId(), transfer(deptB, null, "  ")));
        assertTrue(blank.getMessage().contains("调拨原因"));

        BusinessException same = assertThrows(BusinessException.class,
                () -> lifecycleService.transfer(admin, asset.getId(), transfer(deptA, locationA, "原样")));
        assertTrue(same.getMessage().contains("均未变化"));
        assertEquals(AssetStatus.IN_STOCK.name(), assetDao.selectById(asset.getId()).getStatus());
        assertEquals(deptA, assetDao.selectById(asset.getId()).getDeptId());
    }

    @Test
    void transferDisabledDeptFails() {
        AssetVO asset = createAsset("LC-TF-OFF", deptA, locationA);
        SysDeptDO disabled = new SysDeptDO();
        disabled.setName("停用处" + SEQ.getAndIncrement());
        disabled.setEnabled(0);
        sysDeptDao.insert(disabled);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> lifecycleService.transfer(admin, asset.getId(), transfer(disabled.getId(), null, "调去停用部门")));
        assertTrue(ex.getMessage().contains("停用"));
        assertEquals(deptA, assetDao.selectById(asset.getId()).getDeptId());
    }

    @Test
    void transferBorrowedAssetKeepsBorrowedAndWritesLog() {
        AssetVO asset = createAsset("LC-TF-BR", deptA, locationA);
        issue(asset.getId());

        lifecycleService.transfer(admin, asset.getId(), transfer(deptB, locationB, "随人换房间"));

        AssetDO stored = assetDao.selectById(asset.getId());
        assertEquals(AssetStatus.BORROWED.name(), stored.getStatus());
        assertEquals(deptB, stored.getDeptId());
        assertEquals(locationB, stored.getLocationId());
        assertEquals(user.getUserId(), stored.getHolderUserId());
        assertFalse(logs(asset.getId(), AssetLogAction.TRANSFER).isEmpty());
        assertFalse(audits(asset.getAssetNo(), "TRANSFER").isEmpty());
    }

    @Test
    void borrowedAssetCannotStartRepair() {
        AssetVO asset = createAsset("LC-RP-BR", deptA, locationA);
        issue(asset.getId());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> lifecycleService.startRepair(admin, asset.getId(), repairStart("屏幕闪烁", null)));

        assertTrue(ex.getMessage().contains(AssetStatus.BORROWED.getLabel()));
        assertTrue(ex.getMessage().contains("归还"));
        assertEquals(AssetStatus.BORROWED.name(), assetDao.selectById(asset.getId()).getStatus());
    }

    @Test
    void pendingAssetCannotStartRepairAndShowsStatus() {
        AssetVO asset = createAsset("LC-RP-PE", deptA, locationA);
        borrowService.save(user, submitDto(asset.getId()));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> lifecycleService.startRepair(admin, asset.getId(), repairStart("无法开机", null)));

        assertTrue(ex.getMessage().contains(AssetStatus.PENDING.getLabel()));
        assertEquals(AssetStatus.PENDING.name(), assetDao.selectById(asset.getId()).getStatus());
    }

    @Test
    void startRepairBlocksBorrowUntilFinishedThenCanApplyAgain() {
        AssetVO asset = createAsset("LC-RP-OK", deptA, locationA);
        long before = statsService.overview(admin).getRepairing();
        lifecycleService.startRepair(admin, asset.getId(), repairStart("键盘失灵", LocalDate.now()));

        assertEquals(AssetStatus.REPAIRING.name(), assetDao.selectById(asset.getId()).getStatus());
        assertEquals(before + 1, statsService.overview(admin).getRepairing());
        AssetDetailVO repairing = assetService.get(asset.getId(), admin);
        assertEquals("键盘失灵", repairing.getRepairFault());
        assertEquals(LocalDate.now(), repairing.getRepairSentDate());

        BusinessException occupied = assertThrows(BusinessException.class,
                () -> borrowService.save(user, submitDto(asset.getId())));
        assertTrue(occupied.getMessage().contains(AssetStatus.REPAIRING.getLabel()));
        assertEquals(AssetStatus.REPAIRING.name(), assetDao.selectById(asset.getId()).getStatus());

        ConflictException again = assertThrows(ConflictException.class,
                () -> lifecycleService.startRepair(admin, asset.getId(), repairStart("再送一次", null)));
        assertEquals(HttpStatus.CONFLICT, again.getStatus());
        assertEquals(AssetStatus.REPAIRING.name(), assetDao.selectById(asset.getId()).getStatus());

        lifecycleService.finishRepair(admin, asset.getId(), repairFinish("更换键盘", LocalDate.now()));
        assertEquals(AssetStatus.IN_STOCK.name(), assetDao.selectById(asset.getId()).getStatus());
        assertEquals(before, statsService.overview(admin).getRepairing());

        ConflictException finishAgain = assertThrows(ConflictException.class,
                () -> lifecycleService.finishRepair(admin, asset.getId(), repairFinish("重复完成", null)));
        assertEquals(HttpStatus.CONFLICT, finishAgain.getStatus());
        assertEquals("状态已变化，请刷新", finishAgain.getMessage());
        assertEquals(AssetStatus.IN_STOCK.name(), assetDao.selectById(asset.getId()).getStatus());

        BorrowOrderVO order = borrowService.save(user, submitDto(asset.getId()));
        assertEquals("PENDING", order.getStatus());
        assertEquals(AssetStatus.PENDING.name(), assetDao.selectById(asset.getId()).getStatus());
    }

    @Test
    void finishDateBeforeSentDateFailsAndKeepsRepairing() {
        AssetVO asset = createAsset("LC-RP-DATE", deptA, locationA);
        lifecycleService.startRepair(admin, asset.getId(), repairStart("风扇异响", LocalDate.now()));

        AssetRepairFinishDTO dto = repairFinish("已修好", LocalDate.now().minusDays(1));
        BusinessException ex = assertThrows(BusinessException.class,
                () -> lifecycleService.finishRepair(admin, asset.getId(), dto));
        assertTrue(ex.getMessage().contains("完成日"));
        assertEquals(AssetStatus.REPAIRING.name(), assetDao.selectById(asset.getId()).getStatus());
    }

    @Test
    void scrapWithoutReasonFails() {
        AssetVO asset = createAsset("LC-SC-BLANK", deptA, locationA);
        AssetScrapDTO dto = new AssetScrapDTO();
        dto.setReason("  ");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> lifecycleService.scrap(admin, asset.getId(), dto));
        assertTrue(ex.getMessage().contains("报废原因"));
        assertEquals(AssetStatus.IN_STOCK.name(), assetDao.selectById(asset.getId()).getStatus());
    }

    @Test
    void borrowedAssetCannotBeScrapped() {
        AssetVO asset = createAsset("LC-SC-BR", deptA, locationA);
        issue(asset.getId());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> scrap(asset.getId(), "想报废"));
        assertTrue(ex.getMessage().contains(AssetStatus.BORROWED.getLabel()));
        assertTrue(ex.getMessage().contains("归还"));
        assertEquals(AssetStatus.BORROWED.name(), assetDao.selectById(asset.getId()).getStatus());
    }

    @Test
    void scrapInStockBecomesReadOnlyAndKeepsBorrowHistory() {
        AssetVO asset = createAsset("LC-SC-OK", deptA, locationA);
        BorrowOrderVO order = borrowService.save(user, submitDto(asset.getId()));
        borrowService.approve(admin, order.getId());
        borrowService.issue(admin, order.getId());
        borrowService.confirmReturn(admin, order.getId(), "完好");
        long before = statsService.overview(admin).getScrapped();

        scrap(asset.getId(), "超过使用年限");

        AssetDO stored = assetDao.selectById(asset.getId());
        assertEquals(AssetStatus.SCRAPPED.name(), stored.getStatus());
        assertEquals(before + 1, statsService.overview(admin).getScrapped());
        AssetDetailVO detail = assetService.get(asset.getId(), admin);
        assertFalse(detail.getLogs().isEmpty());
        assertFalse(detail.getLifecycleLogs().isEmpty());

        BusinessException update = assertThrows(BusinessException.class,
                () -> assetService.update(asset.getId(), saveDto(asset.getAssetNo(), deptA, locationA)));
        assertTrue(update.getMessage().contains("只读"));

        BusinessException remove = assertThrows(BusinessException.class, () -> assetService.remove(asset.getId()));
        assertTrue(remove.getMessage().contains("在库"));

        ConflictException twice = assertThrows(ConflictException.class, () -> scrap(asset.getId(), "再报废"));
        assertEquals(HttpStatus.CONFLICT, twice.getStatus());
        assertEquals(AssetStatus.SCRAPPED.name(), assetDao.selectById(asset.getId()).getStatus());

        BusinessException apply = assertThrows(BusinessException.class,
                () -> borrowService.save(user, submitDto(asset.getId())));
        assertTrue(apply.getMessage().contains(AssetStatus.SCRAPPED.getLabel()));

        NotFoundException hidden = assertThrows(NotFoundException.class,
                () -> assetService.get(asset.getId(), other));
        assertEquals(HttpStatus.NOT_FOUND, hidden.getStatus());
    }

    @Test
    void scrapRepairingAssetClosesRepair() {
        AssetVO asset = createAsset("LC-SC-RP", deptA, locationA);
        lifecycleService.startRepair(admin, asset.getId(), repairStart("主板损坏", LocalDate.now()));

        scrap(asset.getId(), "维修成本过高");

        AssetDO stored = assetDao.selectById(asset.getId());
        assertEquals(AssetStatus.SCRAPPED.name(), stored.getStatus());
        AssetDetailVO detail = assetService.get(asset.getId(), admin);
        assertEquals(null, detail.getRepairFault());
        assertTrue(detail.getLifecycleLogs().stream().anyMatch(item -> "SCRAP".equals(item.getAction())));
    }

    private void scrap(Long assetId, String reason) {
        AssetScrapDTO dto = new AssetScrapDTO();
        dto.setReason(reason);
        lifecycleService.scrap(admin, assetId, dto);
    }

    private void issue(Long assetId) {
        BorrowOrderVO order = borrowService.save(user, submitDto(assetId));
        borrowService.approve(admin, order.getId());
        borrowService.issue(admin, order.getId());
    }

    private AssetVO createAsset(String assetNo, Long deptId, Long locationId) {
        return assetService.save(saveDto(assetNo + "-" + SEQ.getAndIncrement(), deptId, locationId));
    }

    private AssetSaveDTO saveDto(String assetNo, Long deptId, Long locationId) {
        AssetSaveDTO dto = new AssetSaveDTO();
        dto.setAssetNo(assetNo);
        dto.setName("生命周期设备");
        dto.setCategoryId(categoryId);
        dto.setDeptId(deptId);
        dto.setLocationId(locationId);
        return dto;
    }

    private AssetTransferDTO transfer(Long deptId, Long locationId, String reason) {
        AssetTransferDTO dto = new AssetTransferDTO();
        dto.setDeptId(deptId);
        dto.setLocationId(locationId);
        dto.setReason(reason);
        return dto;
    }

    private AssetRepairStartDTO repairStart(String fault, LocalDate sentDate) {
        AssetRepairStartDTO dto = new AssetRepairStartDTO();
        dto.setFault(fault);
        dto.setSentDate(sentDate);
        return dto;
    }

    private AssetRepairFinishDTO repairFinish(String result, LocalDate finishedDate) {
        AssetRepairFinishDTO dto = new AssetRepairFinishDTO();
        dto.setResult(result);
        dto.setFinishedDate(finishedDate);
        return dto;
    }

    private BorrowCreateDTO submitDto(Long assetId) {
        BorrowCreateDTO dto = new BorrowCreateDTO();
        dto.setAssetId(assetId);
        dto.setPurpose("课题使用");
        dto.setExpectedReturnDate(LocalDate.now().plusDays(7));
        dto.setSubmit(true);
        return dto;
    }

    private List<AssetLogDO> logs(Long assetId, AssetLogAction action) {
        return assetLogDao.selectList(Wrappers.<AssetLogDO>lambdaQuery()
                .eq(AssetLogDO::getAssetId, assetId)
                .eq(AssetLogDO::getAction, action.name()));
    }

    private List<AuditLogDO> audits(String assetNo, String action) {
        return auditLogDao.selectList(Wrappers.<AuditLogDO>lambdaQuery()
                .eq(AuditLogDO::getObjectNo, assetNo)
                .eq(AuditLogDO::getAction, action));
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
