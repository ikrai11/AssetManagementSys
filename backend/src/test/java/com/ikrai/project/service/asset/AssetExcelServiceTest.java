package com.ikrai.project.service.asset;

import com.alibaba.excel.EasyExcel;
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
import com.ikrai.project.excel.AssetExportRow;
import com.ikrai.project.excel.AssetImportRow;
import com.ikrai.project.query.AssetQuery;
import com.ikrai.project.vo.AssetImportVO;
import com.ikrai.project.vo.AssetVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AssetExcelServiceTest {

    private static final AtomicInteger SEQ = new AtomicInteger(1);

    @Autowired
    private AssetExcelService assetExcelService;
    @Autowired
    private AssetService assetService;
    @Autowired
    private AssetDao assetDao;
    @Autowired
    private AssetCategoryDao assetCategoryDao;
    @Autowired
    private SysUserDao sysUserDao;
    @Autowired
    private AuditLogDao auditLogDao;
    @Autowired
    private PasswordEncoder passwordEncoder;

    private AuthUser admin;

    @BeforeEach
    void setUp() {
        admin = asUser(insertUser("excel_admin", UserRole.ADMIN));
    }

    @Test
    void importValidRowBecomesInStock() {
        AssetImportRow row = baseRow("IMP-" + SEQ.getAndIncrement(), "导入笔记本");
        row.setPurchasePrice("8800.50");
        row.setDeptName("信息中心");
        row.setLocationName("设备仓库");

        AssetImportVO result = assetExcelService.importAssets(admin, excel(List.of(example(), row)));

        assertEquals(1, result.getSuccessCount());
        assertEquals(0, result.getFailCount());
        AssetDO saved = assetDao.selectOne(com.baomidou.mybatisplus.core.toolkit.Wrappers.<AssetDO>lambdaQuery()
                .eq(AssetDO::getAssetNo, row.getAssetNo()));
        assertEquals(AssetStatus.IN_STOCK.name(), saved.getStatus());
        assertEquals(new BigDecimal("8800.50"), saved.getPurchasePrice());
        assertTrue(auditLogDao.selectCount(com.baomidou.mybatisplus.core.toolkit.Wrappers.<AuditLogDO>lambdaQuery()
                .eq(AuditLogDO::getAction, "IMPORT")
                .eq(AuditLogDO::getOperatorId, admin.getUserId())) >= 1);
    }

    @Test
    void importSkipsExampleAndKeepsValid() {
        AssetImportRow valid = baseRow("IMP-" + SEQ.getAndIncrement(), "真实设备");
        AssetImportVO result = assetExcelService.importAssets(admin, excel(List.of(example(), valid)));
        assertEquals(1, result.getSuccessCount());
        assertEquals(0, assetDao.selectCount(com.baomidou.mybatisplus.core.toolkit.Wrappers.<AssetDO>lambdaQuery()
                .eq(AssetDO::getAssetNo, "EXAMPLE")));
    }

    @Test
    void importDuplicateAssetNoFailsRowAndKeepsValid() {
        AssetSaveDTO existing = new AssetSaveDTO();
        existing.setAssetNo("DUP-" + SEQ.getAndIncrement());
        existing.setName("已在库");
        existing.setCategoryId(assetCategoryDao.selectById(1L).getId());
        assetService.save(existing);

        AssetImportRow bad = baseRow(existing.getAssetNo(), "重复编号");
        AssetImportRow good = baseRow("OK-" + SEQ.getAndIncrement(), "合法设备");
        AssetImportVO result = assetExcelService.importAssets(admin, excel(List.of(example(), bad, good)));

        assertEquals(1, result.getSuccessCount());
        assertEquals(1, result.getFailCount());
        assertTrue(result.getFailures().get(0).getReason().contains("资产编号"));
        assertEquals(AssetStatus.IN_STOCK.name(),
                assetDao.selectOne(com.baomidou.mybatisplus.core.toolkit.Wrappers.<AssetDO>lambdaQuery()
                        .eq(AssetDO::getAssetNo, good.getAssetNo())).getStatus());
    }

    @Test
    void importUnknownCategoryFailsRow() {
        AssetImportRow row = baseRow("CAT-" + SEQ.getAndIncrement(), "未知类型");
        row.setCategoryName("不存在的类型");
        AssetImportVO result = assetExcelService.importAssets(admin, excel(List.of(row)));
        assertEquals(0, result.getSuccessCount());
        assertEquals(1, result.getFailCount());
        assertEquals("设备类型不存在", result.getFailures().get(0).getReason());
    }

    @Test
    void importEmptyFileRejected() {
        MockMultipartFile file = new MockMultipartFile("file", "empty.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", new byte[0]);
        BusinessException ex = assertThrows(BusinessException.class, () -> assetExcelService.importAssets(admin, file));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertEquals("空文件", ex.getMessage());
    }

    @Test
    void importHeaderMismatchRejected() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        EasyExcel.write(out).head(List.of(List.of("错误列"))).sheet("设备").doWrite(List.of(List.of("x")));
        MockMultipartFile file = fileOf(out.toByteArray());
        BusinessException ex = assertThrows(BusinessException.class, () -> assetExcelService.importAssets(admin, file));
        assertEquals("表头不匹配", ex.getMessage());
        assertEquals(0, countByPrefix("HDR-"));
    }

    @Test
    void importOverLimitRejected() {
        List<AssetImportRow> rows = new ArrayList<>();
        for (int i = 0; i < 2001; i++) {
            rows.add(baseRow("LIM-" + SEQ.getAndIncrement(), "超限" + i));
        }
        BusinessException ex = assertThrows(BusinessException.class,
                () -> assetExcelService.importAssets(admin, excel(rows)));
        assertTrue(ex.getMessage().contains("上限"));
        assertEquals(0, countByPrefix("LIM-"));
    }

    @Test
    void exportEmptyRejected() {
        AssetQuery query = new AssetQuery();
        query.setKeyword("NO-SUCH-ASSET-" + SEQ.getAndIncrement());
        BusinessException ex = assertThrows(BusinessException.class,
                () -> assetExcelService.exportAssets(query, admin));
        assertEquals("没有可导出的设备", ex.getMessage());
    }

    @Test
    void exportUsesCurrentFilterAndIncludesPurchasePrice() {
        AssetSaveDTO match = new AssetSaveDTO();
        match.setAssetNo("EXP-A-" + SEQ.getAndIncrement());
        match.setName("导出匹配");
        match.setCategoryId(assetCategoryDao.selectById(1L).getId());
        match.setPurchasePrice(new BigDecimal("1999.00"));
        AssetVO saved = assetService.save(match);

        AssetSaveDTO other = new AssetSaveDTO();
        other.setAssetNo("EXP-B-" + SEQ.getAndIncrement());
        other.setName("导出不匹配");
        other.setCategoryId(assetCategoryDao.selectById(2L).getId());
        assetService.save(other);

        AssetQuery query = new AssetQuery();
        query.setKeyword(saved.getAssetNo());
        byte[] bytes = assetExcelService.exportAssets(query, admin);
        List<AssetExportRow> rows = EasyExcel.read(new ByteArrayInputStream(bytes))
                .head(AssetExportRow.class)
                .sheet()
                .doReadSync();
        assertEquals(1, rows.size());
        assertEquals(saved.getAssetNo(), rows.get(0).getAssetNo());
        assertEquals("1999.00", rows.get(0).getPurchasePrice());
        assertTrue(auditLogDao.selectCount(com.baomidou.mybatisplus.core.toolkit.Wrappers.<AuditLogDO>lambdaQuery()
                .eq(AuditLogDO::getAction, "EXPORT")
                .eq(AuditLogDO::getOperatorId, admin.getUserId())) >= 1);
    }

    private long countByPrefix(String prefix) {
        return assetDao.selectCount(com.baomidou.mybatisplus.core.toolkit.Wrappers.<AssetDO>lambdaQuery()
                .likeRight(AssetDO::getAssetNo, prefix));
    }

    private AssetImportRow example() {
        AssetImportRow row = baseRow("EXAMPLE", "示例行");
        row.setSerialNo("SN-EXAMPLE");
        return row;
    }

    private AssetImportRow baseRow(String assetNo, String name) {
        AssetImportRow row = new AssetImportRow();
        row.setAssetNo(assetNo);
        row.setName(name);
        row.setCategoryName("笔记本");
        return row;
    }

    private MockMultipartFile excel(List<AssetImportRow> rows) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        EasyExcel.write(out, AssetImportRow.class).sheet("设备").doWrite(rows);
        return fileOf(out.toByteArray());
    }

    private MockMultipartFile fileOf(byte[] bytes) {
        return new MockMultipartFile("file", "import.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", bytes);
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
