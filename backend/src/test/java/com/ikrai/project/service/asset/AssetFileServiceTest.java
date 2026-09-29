package com.ikrai.project.service.asset;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.ikrai.project.common.AuthUser;
import com.ikrai.project.common.enums.AssetFileKind;
import com.ikrai.project.common.enums.AssetStatus;
import com.ikrai.project.common.enums.UserRole;
import com.ikrai.project.common.exception.BusinessException;
import com.ikrai.project.common.exception.NotFoundException;
import com.ikrai.project.dao.AssetCategoryDao;
import com.ikrai.project.dao.AssetFileDao;
import com.ikrai.project.dao.AuditLogDao;
import com.ikrai.project.dao.SysUserDao;
import com.ikrai.project.dataobject.AssetFileDO;
import com.ikrai.project.dataobject.AuditLogDO;
import com.ikrai.project.dataobject.SysUserDO;
import com.ikrai.project.dto.AssetSaveDTO;
import com.ikrai.project.dto.AssetScrapDTO;
import com.ikrai.project.dto.BorrowCreateDTO;
import com.ikrai.project.service.borrow.BorrowService;
import com.ikrai.project.vo.AssetFileContent;
import com.ikrai.project.vo.AssetFileVO;
import com.ikrai.project.vo.AssetVO;
import com.ikrai.project.vo.BorrowOrderVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AssetFileServiceTest {

    private static final AtomicInteger SEQ = new AtomicInteger(1);
    private static final byte[] PNG = new byte[]{
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00
    };
    private static final byte[] PDF = "%PDF-1.4\n".getBytes();

    @Autowired
    private AssetFileService assetFileService;
    @Autowired
    private AssetService assetService;
    @Autowired
    private AssetLifecycleService lifecycleService;
    @Autowired
    private BorrowService borrowService;
    @Autowired
    private AssetFileDao assetFileDao;
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
    private AuthUser other;
    private Long categoryId;

    @BeforeEach
    void setUp() {
        categoryId = assetCategoryDao.selectById(1L).getId();
        admin = asUser(insertUser("file_admin", UserRole.ADMIN));
        user = asUser(insertUser("file_user", UserRole.USER));
        other = asUser(insertUser("file_other", UserRole.USER));
    }

    @Test
    void userSeesPhotoButNotInvoiceAndCannotDownloadInvoice() {
        AssetVO asset = createAsset();
        AssetFileVO photo = assetFileService.upload(admin, asset.getId(), "PHOTO", file("现场.png", PNG));
        AssetFileVO invoice = assetFileService.upload(admin, asset.getId(), "INVOICE", file("发票.pdf", PDF));

        List<AssetFileVO> adminList = assetFileService.list(admin, asset.getId());
        assertEquals(2, adminList.size());
        assertTrue(adminList.stream().anyMatch(item -> "票据".equals(item.getKindLabel())));

        List<AssetFileVO> userList = assetFileService.list(user, asset.getId());
        assertEquals(1, userList.size());
        assertEquals(AssetFileKind.PHOTO.name(), userList.get(0).getKind());
        assertEquals("照片", userList.get(0).getKindLabel());
        assertEquals(photo.getId(), userList.get(0).getId());

        AssetFileContent downloaded = assetFileService.download(user, asset.getId(), photo.getId());
        assertEquals("image/png", downloaded.getContentType());
        assertArrayEquals(PNG, downloaded.getBytes());

        NotFoundException hidden = assertThrows(NotFoundException.class,
                () -> assetFileService.download(user, asset.getId(), invoice.getId()));
        assertEquals("附件不存在", hidden.getMessage());
    }

    @Test
    void otherUserCannotSeeFilesOfBorrowedAsset() {
        AssetVO asset = createAsset();
        assetFileService.upload(admin, asset.getId(), "PHOTO", file("现场.png", PNG));
        BorrowOrderVO order = borrowService.save(user, submit(asset.getId()));
        borrowService.approve(admin, order.getId());
        borrowService.issue(admin, order.getId());

        NotFoundException hidden = assertThrows(NotFoundException.class,
                () -> assetFileService.list(other, asset.getId()));
        assertEquals("设备不存在", hidden.getMessage());
        assertEquals(HttpStatus.NOT_FOUND, hidden.getStatus());
    }

    @Test
    void deleteWritesAuditAndRemovesFile() {
        AssetVO asset = createAsset();
        AssetFileVO photo = assetFileService.upload(admin, asset.getId(), "PHOTO", file("现场.png", PNG));

        assetFileService.delete(admin, asset.getId(), photo.getId());

        assertEquals(0, assetFileDao.selectCount(Wrappers.<AssetFileDO>lambdaQuery()
                .eq(AssetFileDO::getId, photo.getId())));
        List<AuditLogDO> logs = auditLogDao.selectList(Wrappers.<AuditLogDO>lambdaQuery()
                .eq(AuditLogDO::getObjectNo, asset.getAssetNo())
                .eq(AuditLogDO::getAction, "DELETE"));
        assertEquals(1, logs.size());
        assertEquals("ASSET", logs.get(0).getModule());
        assertTrue(logs.get(0).getSummary().contains("删除照片"));
        assertTrue(logs.get(0).getSummary().contains("现场.png"));
        NotFoundException missing = assertThrows(NotFoundException.class,
                () -> assetFileService.download(admin, asset.getId(), photo.getId()));
        assertEquals("附件不存在", missing.getMessage());
    }

    @Test
    void rejectUnknownTypeOversizedAndUserUpload() {
        AssetVO asset = createAsset();
        BusinessException type = assertThrows(BusinessException.class,
                () -> assetFileService.upload(admin, asset.getId(), "PHOTO", file("a.exe", new byte[]{'M', 'Z'})));
        assertEquals("只支持图片和 pdf", type.getMessage());

        byte[] huge = new byte[10 * 1024 * 1024 + 1];
        huge[0] = (byte) 0xFF;
        huge[1] = (byte) 0xD8;
        huge[2] = (byte) 0xFF;
        BusinessException size = assertThrows(BusinessException.class,
                () -> assetFileService.upload(admin, asset.getId(), "PHOTO", file("big.jpg", huge)));
        assertEquals("文件不能超过 10MB", size.getMessage());

        BusinessException kind = assertThrows(BusinessException.class,
                () -> assetFileService.upload(admin, asset.getId(), "SECRET", file("现场.png", PNG)));
        assertEquals("不支持的附件类型", kind.getMessage());

        BusinessException denied = assertThrows(BusinessException.class,
                () -> assetFileService.upload(user, asset.getId(), "PHOTO", file("现场.png", PNG)));
        assertEquals(HttpStatus.FORBIDDEN, denied.getStatus());
        assertEquals(0, assetFileDao.selectCount(Wrappers.<AssetFileDO>lambdaQuery()
                .eq(AssetFileDO::getAssetId, asset.getId())));
    }

    @Test
    void scrappedAssetRejectsUploadAndDelete() {
        AssetVO asset = createAsset();
        AssetFileVO photo = assetFileService.upload(admin, asset.getId(), "PHOTO", file("现场.png", PNG));
        AssetScrapDTO scrap = new AssetScrapDTO();
        scrap.setReason("无法修复");
        lifecycleService.scrap(admin, asset.getId(), scrap);

        BusinessException upload = assertThrows(BusinessException.class,
                () -> assetFileService.upload(admin, asset.getId(), "PHOTO", file("再拍.png", PNG)));
        assertTrue(upload.getMessage().contains("已报废"));
        BusinessException delete = assertThrows(BusinessException.class,
                () -> assetFileService.delete(admin, asset.getId(), photo.getId()));
        assertTrue(delete.getMessage().contains("已报废"));
        assertEquals(AssetStatus.SCRAPPED.name(), assetService.get(asset.getId(), admin).getStatus());
        assertEquals(1, assetFileService.list(admin, asset.getId()).size());
    }

    private MultipartFile file(String name, byte[] bytes) {
        return new MockMultipartFile("file", name, null, bytes);
    }

    private AssetVO createAsset() {
        AssetSaveDTO dto = new AssetSaveDTO();
        dto.setAssetNo("FILE-" + SEQ.getAndIncrement());
        dto.setName("附件设备");
        dto.setCategoryId(categoryId);
        return assetService.save(dto);
    }

    private BorrowCreateDTO submit(Long assetId) {
        BorrowCreateDTO dto = new BorrowCreateDTO();
        dto.setAssetId(assetId);
        dto.setPurpose("课题使用");
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
