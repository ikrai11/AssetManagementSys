package com.ikrai.project.service.asset;

import com.ikrai.project.common.AuthUser;
import com.ikrai.project.common.enums.UserRole;
import com.ikrai.project.common.exception.BusinessException;
import com.ikrai.project.dao.AssetCategoryDao;
import com.ikrai.project.dao.SysUserDao;
import com.ikrai.project.dataobject.SysUserDO;
import com.ikrai.project.dto.AssetSaveDTO;
import com.ikrai.project.query.AssetQuery;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = "ams.excel.export-max-rows=1")
@Transactional
class AssetExcelExportLimitTest {

    private static final AtomicInteger SEQ = new AtomicInteger(1);

    @Autowired
    private AssetExcelService assetExcelService;
    @Autowired
    private AssetService assetService;
    @Autowired
    private AssetCategoryDao assetCategoryDao;
    @Autowired
    private SysUserDao sysUserDao;
    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void exportOverLimitReturns400() {
        Long categoryId = assetCategoryDao.selectById(1L).getId();
        assetService.save(asset("LIM-A-" + SEQ.getAndIncrement(), categoryId));
        assetService.save(asset("LIM-B-" + SEQ.getAndIncrement(), categoryId));
        SysUserDO user = new SysUserDO();
        user.setUsername("excel_limit" + SEQ.getAndIncrement());
        user.setPasswordHash(passwordEncoder.encode("Passw0rd"));
        user.setRealName("限制管理员");
        user.setEmail("limit@example.com");
        user.setRole(UserRole.ADMIN.name());
        user.setEnabled(1);
        user.setTokenVersion(0);
        user.setMustChangePassword(0);
        sysUserDao.insert(user);
        AuthUser admin = new AuthUser(user.getId(), user.getUsername(), user.getRole(), user.getTokenVersion());

        AssetQuery query = new AssetQuery();
        query.setKeyword("LIM-");
        BusinessException ex = assertThrows(BusinessException.class,
                () -> assetExcelService.exportAssets(query, admin));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertTrue(ex.getMessage().contains("缩小筛选"));
    }

    private AssetSaveDTO asset(String assetNo, Long categoryId) {
        AssetSaveDTO dto = new AssetSaveDTO();
        dto.setAssetNo(assetNo);
        dto.setName(assetNo);
        dto.setCategoryId(categoryId);
        return dto;
    }
}
