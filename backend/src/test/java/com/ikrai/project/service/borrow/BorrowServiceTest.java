package com.ikrai.project.service.borrow;

import com.ikrai.project.common.AuthUser;
import com.ikrai.project.common.enums.AssetStatus;
import com.ikrai.project.common.enums.BorrowOrderStatus;
import com.ikrai.project.common.enums.UserRole;
import com.ikrai.project.common.exception.BusinessException;
import com.ikrai.project.common.exception.ConflictException;
import com.ikrai.project.dao.AssetCategoryDao;
import com.ikrai.project.dao.AssetDao;
import com.ikrai.project.dao.SysUserDao;
import com.ikrai.project.dataobject.AssetCategoryDO;
import com.ikrai.project.dataobject.AssetDO;
import com.ikrai.project.dataobject.SysUserDO;
import com.ikrai.project.dto.AssetSaveDTO;
import com.ikrai.project.dto.BorrowCreateDTO;
import com.ikrai.project.service.asset.AssetService;
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
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class BorrowServiceTest {

    private static final AtomicInteger SEQ = new AtomicInteger(1);

    @Autowired
    private BorrowService borrowService;
    @Autowired
    private AssetService assetService;
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
    private Long categoryId;

    @BeforeEach
    void setUp() {
        AssetCategoryDO category = assetCategoryDao.selectById(1L);
        categoryId = category.getId();
        admin = asUser(insertUser("admin_case", UserRole.ADMIN));
        user = asUser(insertUser("user_case", UserRole.USER));
    }

    @Test
    void applyFailsWhenAssetNotInStockAndShowsCurrentStatus() {
        AssetVO asset = createAsset("NB-BUSY");
        borrowService.save(user, submitDto(asset.getId()));

        AuthUser other = asUser(insertUser("other_user", UserRole.USER));
        BusinessException ex = assertThrows(BusinessException.class,
                () -> borrowService.save(other, submitDto(asset.getId())));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertTrue(ex.getMessage().contains(AssetStatus.PENDING.getLabel()));
        assertEquals(AssetStatus.PENDING.name(), assetDao.selectById(asset.getId()).getStatus());
    }

    @Test
    void rejectWithoutCommentFails() {
        AssetVO asset = createAsset("NB-REJECT");
        BorrowOrderVO order = borrowService.save(user, submitDto(asset.getId()));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> borrowService.reject(admin, order.getId(), "  "));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertTrue(ex.getMessage().contains("原因"));
        assertEquals(BorrowOrderStatus.PENDING.name(),
                borrowService.get(admin, order.getId()).getStatus());
        assertEquals(AssetStatus.PENDING.name(), assetDao.selectById(asset.getId()).getStatus());
    }

    @Test
    void assetStaysPendingUntilIssueThenBecomesBorrowed() {
        AssetVO asset = createAsset("NB-ISSUE");
        BorrowOrderVO order = borrowService.save(user, submitDto(asset.getId()));

        BorrowOrderVO approved = borrowService.approve(admin, order.getId());
        assertEquals(BorrowOrderStatus.APPROVED.name(), approved.getStatus());
        assertEquals(AssetStatus.PENDING.name(), assetDao.selectById(asset.getId()).getStatus());

        BorrowOrderVO issued = borrowService.issue(admin, order.getId());
        assertEquals(BorrowOrderStatus.BORROWING.name(), issued.getStatus());
        assertEquals(AssetStatus.BORROWED.name(), assetDao.selectById(asset.getId()).getStatus());
        assertEquals(user.getUserId(), assetDao.selectById(asset.getId()).getHolderUserId());
    }

    @Test
    void duplicateIssueReturnsConflictAndKeepsAssetBorrowed() {
        AssetVO asset = createAsset("NB-DUP");
        BorrowOrderVO order = borrowService.save(user, submitDto(asset.getId()));
        borrowService.approve(admin, order.getId());
        borrowService.issue(admin, order.getId());

        ConflictException ex = assertThrows(ConflictException.class,
                () -> borrowService.issue(admin, order.getId()));

        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        assertEquals("状态已变化，请刷新", ex.getMessage());
        assertEquals(AssetStatus.BORROWED.name(), assetDao.selectById(asset.getId()).getStatus());
        assertEquals(BorrowOrderStatus.BORROWING.name(),
                borrowService.get(admin, order.getId()).getStatus());
    }

    @Test
    void confirmReturnPutsAssetInStockAndAllowsApplyAgain() {
        AssetVO asset = createAsset("NB-BACK");
        BorrowOrderVO first = borrowService.save(user, submitDto(asset.getId()));
        borrowService.approve(admin, first.getId());
        borrowService.issue(admin, first.getId());

        BorrowOrderVO returned = borrowService.confirmReturn(admin, first.getId(), "外观完好");
        assertEquals(BorrowOrderStatus.RETURNED.name(), returned.getStatus());
        AssetDO afterReturn = assetDao.selectById(asset.getId());
        assertEquals(AssetStatus.IN_STOCK.name(), afterReturn.getStatus());
        assertEquals(null, afterReturn.getHolderUserId());

        BorrowOrderVO second = borrowService.save(user, submitDto(asset.getId()));
        assertEquals(BorrowOrderStatus.PENDING.name(), second.getStatus());
        assertEquals(AssetStatus.PENDING.name(), assetDao.selectById(asset.getId()).getStatus());
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
        SysUserDO user = new SysUserDO();
        user.setUsername(username + SEQ.getAndIncrement());
        user.setPasswordHash(passwordEncoder.encode("Passw0rd"));
        user.setRealName(username);
        user.setEmail(username + "@example.com");
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
