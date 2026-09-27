package com.ikrai.project.service.asset;

import com.ikrai.project.common.AuthUser;
import com.ikrai.project.common.enums.UserRole;
import com.ikrai.project.common.exception.NotFoundException;
import com.ikrai.project.dao.AssetCategoryDao;
import com.ikrai.project.dao.SysUserDao;
import com.ikrai.project.dataobject.SysUserDO;
import com.ikrai.project.dto.AssetSaveDTO;
import com.ikrai.project.dto.BorrowCreateDTO;
import com.ikrai.project.query.AssetQuery;
import com.ikrai.project.service.borrow.BorrowService;
import com.ikrai.project.vo.AssetDetailVO;
import com.ikrai.project.vo.AssetVO;
import com.ikrai.project.vo.PageVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AssetServiceTest {

    private static final AtomicInteger SEQ = new AtomicInteger(1);

    @Autowired
    private AssetService assetService;
    @Autowired
    private BorrowService borrowService;
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
        admin = asUser(insertUser("asset_admin", UserRole.ADMIN));
        user = asUser(insertUser("asset_user", UserRole.USER));
        other = asUser(insertUser("asset_other", UserRole.USER));
    }

    @Test
    void userListDoesNotIncludePurchasePrice() {
        AssetSaveDTO dto = new AssetSaveDTO();
        dto.setAssetNo("PRICE-" + SEQ.getAndIncrement());
        dto.setName("带价格设备");
        dto.setCategoryId(categoryId);
        dto.setPurchasePrice(new BigDecimal("8800.00"));
        dto.setSupplier("某供应商");
        AssetVO saved = assetService.save(dto);

        AssetDetailVO adminView = assetService.get(saved.getId(), admin);
        assertEquals(new BigDecimal("8800.00"), adminView.getPurchasePrice());
        assertEquals("某供应商", adminView.getSupplier());

        PageVO<AssetVO> userPage = assetService.list(new AssetQuery(), user);
        AssetVO userRow = userPage.getList().stream()
                .filter(item -> item.getId().equals(saved.getId()))
                .findFirst()
                .orElseThrow();
        assertNull(userRow.getPurchasePrice());
        assertNull(userRow.getSupplier());
    }

    @Test
    void userCannotSeeOthersBorrowedAsset() {
        AssetSaveDTO dto = new AssetSaveDTO();
        dto.setAssetNo("HIDE-" + SEQ.getAndIncrement());
        dto.setName("他人领用设备");
        dto.setCategoryId(categoryId);
        dto.setPurchasePrice(new BigDecimal("1200.00"));
        AssetVO saved = assetService.save(dto);

        BorrowCreateDTO borrow = new BorrowCreateDTO();
        borrow.setAssetId(saved.getId());
        borrow.setPurpose("本人使用");
        borrow.setExpectedReturnDate(LocalDate.now().plusDays(5));
        borrow.setSubmit(true);
        var order = borrowService.save(user, borrow);
        borrowService.approve(admin, order.getId());
        borrowService.issue(admin, order.getId());

        NotFoundException ex = assertThrows(NotFoundException.class,
                () -> assetService.get(saved.getId(), other));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
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
