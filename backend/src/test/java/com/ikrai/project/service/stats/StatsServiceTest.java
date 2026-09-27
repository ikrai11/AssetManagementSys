package com.ikrai.project.service.stats;

import com.ikrai.project.common.AuthUser;
import com.ikrai.project.common.enums.UserRole;
import com.ikrai.project.dao.AssetCategoryDao;
import com.ikrai.project.dao.AssetDao;
import com.ikrai.project.dao.SysUserDao;
import com.ikrai.project.dataobject.AssetDO;
import com.ikrai.project.dataobject.SysUserDO;
import com.ikrai.project.dto.AssetSaveDTO;
import com.ikrai.project.dto.BorrowCreateDTO;
import com.ikrai.project.service.asset.AssetService;
import com.ikrai.project.service.borrow.BorrowService;
import com.ikrai.project.vo.AssetVO;
import com.ikrai.project.vo.BorrowOrderVO;
import com.ikrai.project.vo.StatsNameCountVO;
import com.ikrai.project.vo.StatsOverviewVO;
import com.ikrai.project.vo.StatsTrendVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class StatsServiceTest {

    private static final AtomicInteger SEQ = new AtomicInteger(1);

    @Autowired
    private StatsService statsService;
    @Autowired
    private AssetService assetService;
    @Autowired
    private BorrowService borrowService;
    @Autowired
    private AssetDao assetDao;
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

    @BeforeEach
    void setUp() {
        categoryId = assetCategoryDao.selectById(1L).getId();
        admin = asUser(insertUser("stat_admin", UserRole.ADMIN));
        user = asUser(insertUser("stat_user", UserRole.USER));
        other = asUser(insertUser("stat_other", UserRole.USER));
    }

    @Test
    void adminOverviewCountsAllAssetsAndPendingOrders() {
        assetService.save(assetDto("ST-IN"));
        BorrowOrderVO pending = borrowService.save(user, submitDto(assetService.save(assetDto("ST-PE")).getId()));
        BorrowOrderVO issued = issue(user, assetService.save(assetDto("ST-BO")).getId());
        setExpectedReturn(issued.getAssetId(), LocalDate.now().minusDays(1));

        StatsOverviewVO vo = statsService.overview(admin);

        assertTrue(vo.getTotal() >= 3);
        assertTrue(vo.getInStock() >= 1);
        assertTrue(vo.getPending() >= 1);
        assertTrue(vo.getBorrowed() >= 1);
        assertEquals(0, vo.getRepairing());
        assertEquals(0, vo.getScrapped());
        assertTrue(vo.getOverdue() >= 1);
        assertTrue(vo.getPendingApproval() >= 1);
        assertEquals(pending.getStatus(), "PENDING");
    }

    @Test
    void dueSoonIsBorrowedWithinSevenDaysAndNotOverdue() {
        BorrowOrderVO soon = issue(user, assetService.save(assetDto("ST-SOON")).getId());
        setExpectedReturn(soon.getAssetId(), LocalDate.now().plusDays(3));
        BorrowOrderVO late = issue(other, assetService.save(assetDto("ST-LATE")).getId());
        setExpectedReturn(late.getAssetId(), LocalDate.now().minusDays(2));

        StatsOverviewVO vo = statsService.overview(admin);
        assertTrue(vo.getDueSoon() >= 1);
        assertTrue(vo.getOverdue() >= 1);
    }

    @Test
    void userOverviewOnlyCountsOwnOrdersAndHeldAssets() {
        BorrowOrderVO mineUsing = issue(user, assetService.save(assetDto("ST-MINE")).getId());
        setExpectedReturn(mineUsing.getAssetId(), LocalDate.now().plusDays(20));
        BorrowOrderVO mineSoon = issue(user, assetService.save(assetDto("ST-MS")).getId());
        setExpectedReturn(mineSoon.getAssetId(), LocalDate.now().plusDays(2));
        BorrowOrderVO otherIssued = issue(other, assetService.save(assetDto("ST-OTH")).getId());
        setExpectedReturn(otherIssued.getAssetId(), LocalDate.now().minusDays(1));
        borrowService.save(user, submitDto(assetService.save(assetDto("ST-APP")).getId()));

        StatsOverviewVO vo = statsService.overview(user);
        assertEquals(1, vo.getMyApplying());
        assertEquals(2, vo.getMyUsing());
        assertEquals(1, vo.getMyDueSoon());
        assertEquals(0, vo.getMyOverdue());
    }

    @Test
    void userCategoryOnlyIncludesOwnBorrowedAssets() {
        AssetVO mine = assetService.save(assetDto("ST-CAT"));
        issue(user, mine.getId());
        issue(other, assetService.save(assetDto("ST-CAT2")).getId());

        List<StatsNameCountVO> mineCats = statsService.byCategory(user);
        long mineTotal = mineCats.stream().mapToLong(StatsNameCountVO::getCount).sum();
        assertEquals(1, mineTotal);

        List<StatsNameCountVO> adminCats = statsService.byCategory(admin);
        long adminTotal = adminCats.stream().mapToLong(StatsNameCountVO::getCount).sum();
        assertTrue(adminTotal >= 2);
    }

    @Test
    void borrowTrendCountsIssuedMonthsAndKeepsSixBuckets() {
        BorrowOrderVO issued = issue(user, assetService.save(assetDto("ST-TR")).getId());
        assertEquals("BORROWING", issued.getStatus());

        List<StatsTrendVO> adminTrend = statsService.borrowTrend(admin);
        assertEquals(6, adminTrend.size());
        assertEquals(YearMonth.now().minusMonths(5).toString(), adminTrend.get(0).getMonth());
        assertEquals(YearMonth.now().toString(), adminTrend.get(5).getMonth());
        assertTrue(adminTrend.stream().mapToLong(StatsTrendVO::getCount).sum() >= 1);

        List<StatsTrendVO> otherTrend = statsService.borrowTrend(other);
        assertEquals(6, otherTrend.size());
        assertEquals(0, otherTrend.stream().mapToLong(StatsTrendVO::getCount).sum());
    }

    private AssetSaveDTO assetDto(String prefix) {
        AssetSaveDTO dto = new AssetSaveDTO();
        dto.setAssetNo(prefix + "-" + SEQ.getAndIncrement());
        dto.setName("统计设备");
        dto.setCategoryId(categoryId);
        return dto;
    }

    private BorrowCreateDTO submitDto(Long assetId) {
        BorrowCreateDTO dto = new BorrowCreateDTO();
        dto.setAssetId(assetId);
        dto.setPurpose("统计用例");
        dto.setExpectedReturnDate(LocalDate.now().plusDays(7));
        dto.setSubmit(true);
        return dto;
    }

    private BorrowOrderVO issue(AuthUser applicant, Long assetId) {
        BorrowOrderVO order = borrowService.save(applicant, submitDto(assetId));
        borrowService.approve(admin, order.getId());
        return borrowService.issue(admin, order.getId());
    }

    private void setExpectedReturn(Long assetId, LocalDate date) {
        AssetDO asset = assetDao.selectById(assetId);
        asset.setExpectedReturnDate(date);
        assetDao.updateById(asset);
    }

    private SysUserDO insertUser(String username, UserRole role) {
        SysUserDO user = new SysUserDO();
        user.setUsername(username + SEQ.getAndIncrement());
        user.setPasswordHash(passwordEncoder.encode("Passw0rd"));
        user.setRealName(username);
        user.setEmail(username + SEQ.get() + "@example.com");
        user.setRole(role.name());
        user.setEnabled(1);
        user.setTokenVersion(0);
        user.setMustChangePassword(0);
        sysUserDao.insert(user);
        return user;
    }

    private AuthUser asUser(SysUserDO user) {
        return new AuthUser(user.getId(), user.getUsername(), user.getRole(), user.getTokenVersion());
    }
}
