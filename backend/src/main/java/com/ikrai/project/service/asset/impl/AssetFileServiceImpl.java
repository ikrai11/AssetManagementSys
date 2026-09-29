package com.ikrai.project.service.asset.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.ikrai.project.common.AuthUser;
import com.ikrai.project.common.enums.AssetFileKind;
import com.ikrai.project.common.enums.AssetStatus;
import com.ikrai.project.common.exception.BusinessException;
import com.ikrai.project.common.exception.NotFoundException;
import com.ikrai.project.config.AmsProperties;
import com.ikrai.project.dao.AssetDao;
import com.ikrai.project.dao.AssetFileDao;
import com.ikrai.project.dao.AuditLogDao;
import com.ikrai.project.dao.BorrowOrderDao;
import com.ikrai.project.dataobject.AssetDO;
import com.ikrai.project.dataobject.AssetFileDO;
import com.ikrai.project.dataobject.AuditLogDO;
import com.ikrai.project.dataobject.BorrowOrderDO;
import com.ikrai.project.service.asset.AssetFileService;
import com.ikrai.project.vo.AssetFileContent;
import com.ikrai.project.vo.AssetFileVO;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class AssetFileServiceImpl implements AssetFileService {

    static final long MAX_BYTES = 10L * 1024 * 1024;

    private final AssetDao assetDao;
    private final AssetFileDao assetFileDao;
    private final BorrowOrderDao borrowOrderDao;
    private final AuditLogDao auditLogDao;
    private final AmsProperties amsProperties;

    public AssetFileServiceImpl(AssetDao assetDao,
                                AssetFileDao assetFileDao,
                                BorrowOrderDao borrowOrderDao,
                                AuditLogDao auditLogDao,
                                AmsProperties amsProperties) {
        this.assetDao = assetDao;
        this.assetFileDao = assetFileDao;
        this.borrowOrderDao = borrowOrderDao;
        this.auditLogDao = auditLogDao;
        this.amsProperties = amsProperties;
    }

    @Override
    public List<AssetFileVO> list(AuthUser viewer, Long assetId) {
        requireVisible(assetId, viewer);
        return assetFileDao.selectList(Wrappers.<AssetFileDO>lambdaQuery()
                        .eq(AssetFileDO::getAssetId, assetId)
                        .orderByDesc(AssetFileDO::getCreatedAt)
                        .orderByDesc(AssetFileDO::getId))
                .stream()
                .filter(file -> viewer.isAdmin() || AssetFileKind.PHOTO.name().equals(file.getKind()))
                .map(this::toVo)
                .toList();
    }

    @Override
    @Transactional
    public AssetFileVO upload(AuthUser operator, Long assetId, String kindName, MultipartFile file) {
        requireAdmin(operator);
        AssetFileKind kind = AssetFileKind.parse(kindName);
        if (kind == null) {
            throw new BusinessException("不支持的附件类型");
        }
        AssetDO asset = requireAsset(assetId);
        if (AssetStatus.SCRAPPED.name().equals(asset.getStatus())) {
            throw new BusinessException("已报废设备只读，不能修改");
        }
        if (file == null || file.isEmpty()) {
            throw new BusinessException("请选择文件");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new BusinessException("文件不能超过 10MB");
        }
        byte[] bytes = readBytes(file);
        if (bytes.length > MAX_BYTES) {
            throw new BusinessException("文件不能超过 10MB");
        }
        String contentType = detectContentType(bytes);
        String extension = extensionOf(contentType);
        String originalName = originalName(file.getOriginalFilename(), extension);
        String storedName = UUID.randomUUID().toString().replace("-", "") + "." + extension;
        Path path = resolveStored(storedName);
        try {
            Files.createDirectories(path.getParent());
            Files.write(path, bytes);
        } catch (IOException ex) {
            throw new BusinessException("文件保存失败");
        }
        try {
            AssetFileDO row = new AssetFileDO();
            row.setAssetId(assetId);
            row.setKind(kind.name());
            row.setOriginalName(originalName);
            row.setStoredName(storedName);
            row.setContentType(contentType);
            row.setSizeBytes((long) bytes.length);
            row.setUploadedBy(operator.getUserId());
            assetFileDao.insert(row);
            writeAudit(operator, asset.getAssetNo(), "CREATE", "上传" + kind.getLabel() + " " + originalName);
            return toVo(assetFileDao.selectById(row.getId()));
        } catch (RuntimeException ex) {
            try {
                Files.deleteIfExists(path);
            } catch (IOException ignored) {
                // 入库失败时尽量清掉已写入的文件
            }
            throw ex;
        }
    }

    @Override
    public AssetFileContent download(AuthUser viewer, Long assetId, Long fileId) {
        requireVisible(assetId, viewer);
        AssetFileDO file = requireFile(assetId, fileId);
        if (!viewer.isAdmin() && AssetFileKind.INVOICE.name().equals(file.getKind())) {
            throw new NotFoundException("附件不存在");
        }
        Path path = resolveStored(file.getStoredName());
        if (!Files.isRegularFile(path)) {
            throw new NotFoundException("附件不存在");
        }
        try {
            return new AssetFileContent(file.getOriginalName(), file.getContentType(), Files.readAllBytes(path));
        } catch (IOException ex) {
            throw new NotFoundException("附件不存在");
        }
    }

    @Override
    @Transactional
    public void delete(AuthUser operator, Long assetId, Long fileId) {
        requireAdmin(operator);
        AssetDO asset = requireAsset(assetId);
        if (AssetStatus.SCRAPPED.name().equals(asset.getStatus())) {
            throw new BusinessException("已报废设备只读，不能修改");
        }
        AssetFileDO file = requireFile(assetId, fileId);
        assetFileDao.deleteById(file.getId());
        AssetFileKind kind = AssetFileKind.parse(file.getKind());
        String label = kind == null ? "附件" : kind.getLabel();
        writeAudit(operator, asset.getAssetNo(), "DELETE", "删除" + label + " " + file.getOriginalName());
        try {
            Files.deleteIfExists(resolveStored(file.getStoredName()));
        } catch (IOException ex) {
            throw new BusinessException("文件删除失败");
        }
    }

    @Override
    @Transactional
    public void deleteAll(Long assetId) {
        List<AssetFileDO> files = assetFileDao.selectList(Wrappers.<AssetFileDO>lambdaQuery()
                .eq(AssetFileDO::getAssetId, assetId));
        if (files.isEmpty()) {
            return;
        }
        assetFileDao.delete(Wrappers.<AssetFileDO>lambdaQuery().eq(AssetFileDO::getAssetId, assetId));
        for (AssetFileDO file : files) {
            try {
                Files.deleteIfExists(resolveStored(file.getStoredName()));
            } catch (IOException ex) {
                throw new BusinessException("文件删除失败");
            }
        }
    }

    static String detectContentType(byte[] data) {
        if (data.length >= 3
                && (data[0] & 0xFF) == 0xFF
                && (data[1] & 0xFF) == 0xD8
                && (data[2] & 0xFF) == 0xFF) {
            return "image/jpeg";
        }
        if (data.length >= 8
                && (data[0] & 0xFF) == 0x89
                && data[1] == 0x50
                && data[2] == 0x4E
                && data[3] == 0x47
                && data[4] == 0x0D
                && data[5] == 0x0A
                && data[6] == 0x1A
                && data[7] == 0x0A) {
            return "image/png";
        }
        if (data.length >= 6
                && data[0] == 'G'
                && data[1] == 'I'
                && data[2] == 'F'
                && data[3] == '8'
                && (data[4] == '7' || data[4] == '9')
                && data[5] == 'a') {
            return "image/gif";
        }
        if (data.length >= 12
                && data[0] == 'R'
                && data[1] == 'I'
                && data[2] == 'F'
                && data[3] == 'F'
                && data[8] == 'W'
                && data[9] == 'E'
                && data[10] == 'B'
                && data[11] == 'P') {
            return "image/webp";
        }
        if (data.length >= 5
                && data[0] == '%'
                && data[1] == 'P'
                && data[2] == 'D'
                && data[3] == 'F'
                && data[4] == '-') {
            return "application/pdf";
        }
        throw new BusinessException("只支持图片和 pdf");
    }

    private void requireAdmin(AuthUser operator) {
        if (operator == null || !operator.isAdmin()) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "无权限");
        }
    }

    private AssetDO requireVisible(Long assetId, AuthUser viewer) {
        AssetDO asset = requireAsset(assetId);
        if (!visibleTo(asset, viewer)) {
            throw new NotFoundException("设备不存在");
        }
        return asset;
    }

    private AssetDO requireAsset(Long assetId) {
        AssetDO asset = assetDao.selectById(assetId);
        if (asset == null) {
            throw new NotFoundException("设备不存在");
        }
        return asset;
    }

    private AssetFileDO requireFile(Long assetId, Long fileId) {
        AssetFileDO file = assetFileDao.selectById(fileId);
        if (file == null || !Objects.equals(file.getAssetId(), assetId)) {
            throw new NotFoundException("附件不存在");
        }
        return file;
    }

    private boolean visibleTo(AssetDO asset, AuthUser viewer) {
        if (viewer != null && viewer.isAdmin()) {
            return true;
        }
        if (viewer == null) {
            return false;
        }
        if (AssetStatus.IN_STOCK.name().equals(asset.getStatus())) {
            return true;
        }
        if (asset.getCurrentBorrowId() == null) {
            return false;
        }
        BorrowOrderDO order = borrowOrderDao.selectById(asset.getCurrentBorrowId());
        return order != null && Objects.equals(order.getApplicantId(), viewer.getUserId());
    }

    private void writeAudit(AuthUser operator, String assetNo, String action, String summary) {
        AuditLogDO log = new AuditLogDO();
        log.setOperatorId(operator.getUserId());
        log.setModule("ASSET");
        log.setAction(action);
        log.setObjectNo(assetNo);
        log.setSummary(StrUtil.sub(summary, 0, 500));
        auditLogDao.insert(log);
    }

    private AssetFileVO toVo(AssetFileDO file) {
        AssetFileVO vo = new AssetFileVO();
        vo.setId(file.getId());
        vo.setAssetId(file.getAssetId());
        vo.setKind(file.getKind());
        AssetFileKind kind = AssetFileKind.parse(file.getKind());
        vo.setKindLabel(kind == null ? file.getKind() : kind.getLabel());
        vo.setOriginalName(file.getOriginalName());
        vo.setContentType(file.getContentType());
        vo.setSizeBytes(file.getSizeBytes());
        vo.setCreatedAt(file.getCreatedAt());
        return vo;
    }

    private Path resolveStored(String storedName) {
        if (storedName == null || !storedName.matches("[a-f0-9]{32}\\.(jpg|png|gif|webp|pdf)")) {
            throw new NotFoundException("附件不存在");
        }
        Path root = root();
        Path path = root.resolve(storedName).normalize();
        if (!path.startsWith(root)) {
            throw new NotFoundException("附件不存在");
        }
        return path;
    }

    private Path root() {
        String dir = amsProperties.getFile().getDir();
        if (dir == null || dir.isBlank()) {
            dir = "data/asset-files";
        }
        return Path.of(dir).toAbsolutePath().normalize();
    }

    private static byte[] readBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException ex) {
            throw new BusinessException("文件读取失败");
        }
    }

    private static String extensionOf(String contentType) {
        return switch (contentType) {
            case "image/jpeg" -> "jpg";
            case "image/png" -> "png";
            case "image/gif" -> "gif";
            case "image/webp" -> "webp";
            case "application/pdf" -> "pdf";
            default -> throw new BusinessException("只支持图片和 pdf");
        };
    }

    private static String originalName(String raw, String extension) {
        String name = raw == null ? "" : Path.of(raw).getFileName().toString();
        name = name.replace("\r", "").replace("\n", "").trim();
        if (name.isBlank() || ".".equals(name) || "..".equals(name)) {
            name = "未命名." + extension;
        }
        if (name.length() > 200) {
            name = name.substring(name.length() - 200);
        }
        return name;
    }
}
