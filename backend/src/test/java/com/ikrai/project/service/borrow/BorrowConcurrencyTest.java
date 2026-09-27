package com.ikrai.project.service.borrow;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.ikrai.project.common.AuthUser;
import com.ikrai.project.common.enums.AssetStatus;
import com.ikrai.project.common.enums.BorrowOrderStatus;
import com.ikrai.project.common.enums.UserRole;
import com.ikrai.project.common.exception.ConflictException;
import com.ikrai.project.dao.AssetCategoryDao;
import com.ikrai.project.dao.AssetDao;
import com.ikrai.project.dao.BorrowOrderDao;
import com.ikrai.project.dao.SysUserDao;
import com.ikrai.project.dataobject.AssetDO;
import com.ikrai.project.dataobject.BorrowOrderDO;
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

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
class BorrowConcurrencyTest {

    private static final AtomicInteger SEQ = new AtomicInteger(1);

    @Autowired
    private BorrowService borrowService;
    @Autowired
    private AssetService assetService;
    @Autowired
    private AssetDao assetDao;
    @Autowired
    private BorrowOrderDao borrowOrderDao;
    @Autowired
    private SysUserDao sysUserDao;
    @Autowired
    private AssetCategoryDao assetCategoryDao;
    @Autowired
    private PasswordEncoder passwordEncoder;

    private AuthUser userA;
    private AuthUser userB;
    private Long categoryId;

    @BeforeEach
    void setUp() {
        categoryId = assetCategoryDao.selectById(1L).getId();
        userA = asUser(insertUser("race_a", UserRole.USER));
        userB = asUser(insertUser("race_b", UserRole.USER));
    }

    @Test
    void concurrentApplyOnSameAssetOneSucceedsOtherConflicts() throws Exception {
        AssetVO asset = createAsset("NB-RACE");
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(2);
        AtomicReference<Object> first = new AtomicReference<>();
        AtomicReference<Object> second = new AtomicReference<>();

        ExecutorService pool = Executors.newFixedThreadPool(2);
        pool.submit(() -> applyWhenReady(userA, asset.getId(), start, done, first));
        pool.submit(() -> applyWhenReady(userB, asset.getId(), start, done, second));
        start.countDown();
        assertTrue(done.await(15, TimeUnit.SECONDS));
        pool.shutdownNow();

        Object winner;
        Object loser;
        if (first.get() instanceof BorrowOrderVO) {
            winner = first.get();
            loser = second.get();
        } else {
            winner = second.get();
            loser = first.get();
        }

        BorrowOrderVO order = assertInstanceOf(BorrowOrderVO.class, winner);
        ConflictException conflict = assertInstanceOf(ConflictException.class, loser);
        assertEquals(HttpStatus.CONFLICT, conflict.getStatus());
        assertEquals("状态已变化，请刷新", conflict.getMessage());
        assertEquals(BorrowOrderStatus.PENDING.name(), order.getStatus());

        AssetDO latest = assetDao.selectById(asset.getId());
        assertEquals(AssetStatus.PENDING.name(), latest.getStatus());
        assertEquals(order.getId(), latest.getCurrentBorrowId());

        List<BorrowOrderDO> pending = borrowOrderDao.selectList(
                Wrappers.<BorrowOrderDO>lambdaQuery()
                        .eq(BorrowOrderDO::getAssetId, asset.getId())
                        .eq(BorrowOrderDO::getStatus, BorrowOrderStatus.PENDING.name()));
        assertEquals(1, pending.size());
    }

    private void applyWhenReady(AuthUser applicant,
                                Long assetId,
                                CountDownLatch start,
                                CountDownLatch done,
                                AtomicReference<Object> result) {
        try {
            start.await();
            result.set(borrowService.save(applicant, submitDto(assetId)));
        } catch (Exception ex) {
            result.set(ex);
        } finally {
            done.countDown();
        }
    }

    private AssetVO createAsset(String assetNo) {
        AssetSaveDTO dto = new AssetSaveDTO();
        dto.setAssetNo(assetNo + "-" + SEQ.getAndIncrement());
        dto.setName("并发测试设备");
        dto.setCategoryId(categoryId);
        return assetService.save(dto);
    }

    private BorrowCreateDTO submitDto(Long assetId) {
        BorrowCreateDTO dto = new BorrowCreateDTO();
        dto.setAssetId(assetId);
        dto.setPurpose("并发申请");
        dto.setExpectedReturnDate(LocalDate.now().plusDays(7));
        dto.setSubmit(true);
        return dto;
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
