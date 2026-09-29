package com.ikrai.project.service.stocktake;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.ikrai.project.common.AuthUser;
import com.ikrai.project.common.enums.AssetStatus;
import com.ikrai.project.common.enums.StocktakeResult;
import com.ikrai.project.common.enums.UserRole;
import com.ikrai.project.common.exception.BusinessException;
import com.ikrai.project.common.exception.ConflictException;
import com.ikrai.project.dao.AssetCategoryDao;
import com.ikrai.project.dao.AssetDao;
import com.ikrai.project.dao.AssetLogDao;
import com.ikrai.project.dao.SysDeptDao;
import com.ikrai.project.dao.SysLocationDao;
import com.ikrai.project.dao.SysUserDao;
import com.ikrai.project.dataobject.AssetDO;
import com.ikrai.project.dataobject.AssetLogDO;
import com.ikrai.project.dataobject.SysDeptDO;
import com.ikrai.project.dataobject.SysLocationDO;
import com.ikrai.project.dataobject.SysUserDO;
import com.ikrai.project.dto.AssetSaveDTO;
import com.ikrai.project.dto.BorrowCreateDTO;
import com.ikrai.project.dto.StocktakeCreateDTO;
import com.ikrai.project.dto.StocktakeMarkDTO;
import com.ikrai.project.service.asset.AssetLifecycleService;
import com.ikrai.project.service.asset.AssetService;
import com.ikrai.project.service.borrow.BorrowService;
import com.ikrai.project.vo.AssetVO;
import com.ikrai.project.vo.BorrowOrderVO;
import com.ikrai.project.vo.StocktakeItemVO;
import com.ikrai.project.vo.StocktakeVO;
import com.ikrai.project.dto.AssetScrapDTO;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class StocktakeServiceTest {

    private static final AtomicInteger SEQ = new AtomicInteger(1);

    @Autowired
    private StocktakeService stocktakeService;
    @Autowired
    private AssetService assetService;
    @Autowired
    private AssetLifecycleService lifecycleService;
    @Autowired
    private BorrowService borrowService;
    @Autowired
    private AssetDao assetDao;
    @Autowired
    private AssetLogDao assetLogDao;
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
    private Long categoryId;
    private Long deptA;
    private Long locationA;
    private Long locationB;

    @BeforeEach
    void setUp() {
        categoryId = assetCategoryDao.selectById(1L).getId();
        deptA = sysDeptDao.selectList(Wrappers.<SysDeptDO>lambdaQuery().eq(SysDeptDO::getEnabled, 1)).get(0).getId();
        var locations = sysLocationDao.selectList(Wrappers.<SysLocationDO>lambdaQuery().eq(SysLocationDO::getEnabled, 1));
        locationA = locations.get(0).getId();
        locationB = locations.get(1).getId();
        admin = asUser(insertUser("stk_admin", UserRole.ADMIN));
        user = asUser(insertUser("stk_user", UserRole.USER));
    }

    @Test
    void createRequiresOneScopeAndRejectsDisabledPlace() {
        BusinessException blank = assertThrows(BusinessException.class,
                () -> stocktakeService.create(admin, createDto(null, null, null)));
        assertTrue(blank.getMessage().contains("部门或按地点"));

        StocktakeCreateDTO both = createDto("LOCATION", deptA, locationA);
        BusinessException mixed = assertThrows(BusinessException.class, () -> stocktakeService.create(admin, both));
        assertTrue(mixed.getMessage().contains("不能同时指定部门"));

        SysLocationDO disabled = new SysLocationDO();
        disabled.setName("停用房间" + SEQ.getAndIncrement());
        disabled.setEnabled(0);
        sysLocationDao.insert(disabled);
        BusinessException off = assertThrows(BusinessException.class,
                () -> stocktakeService.create(admin, createDto("LOCATION", null, disabled.getId())));
        assertTrue(off.getMessage().contains("停用"));
    }

    @Test
    void checklistExcludesScrappedAndOtherPlaces() {
        AssetVO here = save("ST-HERE", deptA, locationA);
        AssetVO other = save("ST-OTHER", deptA, locationB);
        AssetVO gone = save("ST-GONE", deptA, locationA);
        scrap(gone.getId());

        StocktakeVO task = stocktakeService.create(admin, createDto("LOCATION", null, locationA));

        assertTrue(task.getItems().stream().anyMatch(item -> item.getAssetId().equals(here.getId())));
        assertTrue(task.getItems().stream().noneMatch(item -> item.getAssetId().equals(other.getId())));
        assertTrue(task.getItems().stream().noneMatch(item -> item.getAssetId().equals(gone.getId())));
        assertEquals(AssetStatus.IN_STOCK.name(), assetDao.selectById(here.getId()).getStatus());
        assertEquals(AssetStatus.SCRAPPED.name(), assetDao.selectById(gone.getId()).getStatus());
    }

    @Test
    void missingBorrowedAssetDoesNotChangeBorrowOrScrapIt() {
        AssetVO asset = save("ST-BORROW", deptA, locationA);
        BorrowOrderVO order = borrowService.save(user, submit(asset.getId()));
        borrowService.approve(admin, order.getId());
        borrowService.issue(admin, order.getId());

        StocktakeVO task = stocktakeService.create(admin, createDto("DEPT", deptA, null));
        StocktakeItemVO item = task.getItems().stream()
                .filter(row -> row.getAssetId().equals(asset.getId()))
                .findFirst()
                .orElseThrow();
        assertEquals(AssetStatus.BORROWED.name(), item.getAssetStatus());

        StocktakeVO marked = stocktakeService.mark(admin, task.getId(), item.getId(), mark("MISSING", "房间里没有"));
        AssetDO stored = assetDao.selectById(asset.getId());
        assertEquals(AssetStatus.BORROWED.name(), stored.getStatus());
        assertEquals(user.getUserId(), stored.getHolderUserId());
        assertEquals(locationA, stored.getLocationId());
        assertEquals(1, marked.getDifference());
        assertFalse(assetLogDao.selectList(Wrappers.<AssetLogDO>lambdaQuery()
                .eq(AssetLogDO::getAssetId, asset.getId())
                .eq(AssetLogDO::getAction, "STOCKTAKE")).isEmpty());
    }

    @Test
    void mismatchWithoutPlaceFailsAndFinishRequiresEveryItem() {
        AssetVO asset = save("ST-MARK", deptA, locationA);
        StocktakeVO task = stocktakeService.create(admin, createDto("LOCATION", null, locationA));
        StocktakeItemVO item = task.getItems().get(0);

        BusinessException mismatch = assertThrows(BusinessException.class,
                () -> stocktakeService.mark(admin, task.getId(), item.getId(), mark("MISMATCH", "  ")));
        assertTrue(mismatch.getMessage().contains("发现地点"));
        assertEquals(StocktakeResult.PENDING.name(), stocktakeService.get(task.getId()).getItems().get(0).getResult());
        assertEquals(AssetStatus.IN_STOCK.name(), assetDao.selectById(asset.getId()).getStatus());

        BusinessException unfinished = assertThrows(BusinessException.class, () -> stocktakeService.finish(admin, task.getId()));
        assertTrue(unfinished.getMessage().contains("未盘点"));
        assertEquals("OPEN", stocktakeService.get(task.getId()).getStatus());

        stocktakeService.mark(admin, task.getId(), item.getId(), mark("MISMATCH", "在会议室"));
        StocktakeVO finished = stocktakeService.finish(admin, task.getId());
        assertEquals("FINISHED", finished.getStatus());
        assertEquals(1, finished.getDifference());
        assertEquals("在会议室", finished.getItems().get(0).getComment());
        assertEquals(AssetStatus.IN_STOCK.name(), assetDao.selectById(asset.getId()).getStatus());

        ConflictException again = assertThrows(ConflictException.class, () -> stocktakeService.finish(admin, task.getId()));
        assertEquals(HttpStatus.CONFLICT, again.getStatus());
        assertEquals("状态已变化，请刷新", again.getMessage());

        ConflictException remark = assertThrows(ConflictException.class,
                () -> stocktakeService.mark(admin, task.getId(), item.getId(), mark("NORMAL", null)));
        assertEquals(HttpStatus.CONFLICT, remark.getStatus());
        assertEquals(StocktakeResult.MISMATCH.name(), stocktakeService.get(task.getId()).getItems().get(0).getResult());
        assertEquals(locationA, assetDao.selectById(asset.getId()).getLocationId());
    }

    @Test
    void emptyScopeCanFinishWithNoDifference() {
        StocktakeVO task = stocktakeService.create(admin, createDto("LOCATION", null, locationB));
        if (task.getTotal() > 0) {
            for (StocktakeItemVO item : task.getItems()) {
                stocktakeService.mark(admin, task.getId(), item.getId(), mark("NORMAL", null));
            }
        }
        StocktakeVO finished = stocktakeService.finish(admin, task.getId());
        assertEquals("FINISHED", finished.getStatus());
        assertEquals(0, finished.getPending());
    }

    private void scrap(Long assetId) {
        AssetScrapDTO dto = new AssetScrapDTO();
        dto.setReason("盘点前已报废");
        lifecycleService.scrap(admin, assetId, dto);
    }

    private AssetVO save(String assetNo, Long deptId, Long locationId) {
        AssetSaveDTO dto = new AssetSaveDTO();
        dto.setAssetNo(assetNo + "-" + SEQ.getAndIncrement());
        dto.setName("盘点设备");
        dto.setCategoryId(categoryId);
        dto.setDeptId(deptId);
        dto.setLocationId(locationId);
        return assetService.save(dto);
    }

    private StocktakeCreateDTO createDto(String scope, Long deptId, Long locationId) {
        StocktakeCreateDTO dto = new StocktakeCreateDTO();
        dto.setScopeType(scope);
        dto.setDeptId(deptId);
        dto.setLocationId(locationId);
        return dto;
    }

    private StocktakeMarkDTO mark(String result, String comment) {
        StocktakeMarkDTO dto = new StocktakeMarkDTO();
        dto.setResult(result);
        dto.setComment(comment);
        return dto;
    }

    private BorrowCreateDTO submit(Long assetId) {
        BorrowCreateDTO dto = new BorrowCreateDTO();
        dto.setAssetId(assetId);
        dto.setPurpose("盘点占用");
        dto.setExpectedReturnDate(LocalDate.now().plusDays(5));
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
