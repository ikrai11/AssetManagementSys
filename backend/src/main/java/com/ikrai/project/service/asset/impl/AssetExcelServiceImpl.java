package com.ikrai.project.service.asset.impl;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.event.AnalysisEventListener;
import com.alibaba.excel.exception.ExcelAnalysisException;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.ikrai.project.common.AuthUser;
import com.ikrai.project.common.exception.BusinessException;
import com.ikrai.project.config.AmsProperties;
import com.ikrai.project.dao.AssetCategoryDao;
import com.ikrai.project.dao.AuditLogDao;
import com.ikrai.project.dao.SysDeptDao;
import com.ikrai.project.dao.SysLocationDao;
import com.ikrai.project.dataobject.AssetCategoryDO;
import com.ikrai.project.dataobject.AuditLogDO;
import com.ikrai.project.dataobject.SysDeptDO;
import com.ikrai.project.dataobject.SysLocationDO;
import com.ikrai.project.dto.AssetSaveDTO;
import com.ikrai.project.excel.AssetExportRow;
import com.ikrai.project.excel.AssetFailRow;
import com.ikrai.project.excel.AssetImportRow;
import com.ikrai.project.query.AssetQuery;
import com.ikrai.project.service.asset.AssetExcelService;
import com.ikrai.project.service.asset.AssetService;
import com.ikrai.project.vo.AssetImportFailVO;
import com.ikrai.project.vo.AssetImportVO;
import com.ikrai.project.vo.AssetVO;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AssetExcelServiceImpl implements AssetExcelService {

    public static final List<String> IMPORT_HEADERS = List.of(
            "资产编号", "资产名称", "设备类型", "品牌", "型号", "序列号",
            "购置日期", "购置价格", "供应商", "保修截止日期", "责任部门", "存放地点", "备注");

    private final AssetService assetService;
    private final AssetCategoryDao assetCategoryDao;
    private final SysDeptDao sysDeptDao;
    private final SysLocationDao sysLocationDao;
    private final AuditLogDao auditLogDao;
    private final AmsProperties amsProperties;
    private final ConcurrentHashMap<Long, byte[]> failFiles = new ConcurrentHashMap<>();

    public AssetExcelServiceImpl(AssetService assetService,
                                 AssetCategoryDao assetCategoryDao,
                                 SysDeptDao sysDeptDao,
                                 SysLocationDao sysLocationDao,
                                 AuditLogDao auditLogDao,
                                 AmsProperties amsProperties) {
        this.assetService = assetService;
        this.assetCategoryDao = assetCategoryDao;
        this.sysDeptDao = sysDeptDao;
        this.sysLocationDao = sysLocationDao;
        this.auditLogDao = auditLogDao;
        this.amsProperties = amsProperties;
    }

    @Override
    public byte[] template() {
        AssetImportRow example = new AssetImportRow();
        example.setAssetNo("EXAMPLE");
        example.setName("示例笔记本（此行不会导入）");
        example.setCategoryName("笔记本");
        example.setBrand("示例品牌");
        example.setModel("示例型号");
        example.setSerialNo("SN-EXAMPLE");
        example.setPurchaseDate("2026-01-15");
        example.setPurchasePrice("6800.00");
        example.setSupplier("示例供应商");
        example.setWarrantyUntil("2029-01-15");
        example.setDeptName("信息中心");
        example.setLocationName("设备仓库");
        example.setRemark("请删除本行后填写真实设备");
        return write(AssetImportRow.class, "导入模板", List.of(example));
    }

    @Override
    @Transactional
    public AssetImportVO importAssets(AuthUser operator, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("空文件");
        }
        String filename = StrUtil.blankToDefault(file.getOriginalFilename(), "");
        if (!StrUtil.endWithIgnoreCase(filename, ".xlsx")) {
            throw new BusinessException("整文件无法解析，未写入任何数据");
        }
        List<RowHolder> rows = readRows(file);
        int maxRows = amsProperties.getExcel().getImportMaxRows();
        if (rows.size() > maxRows) {
            throw new BusinessException("超过单次导入上限 " + maxRows + " 行");
        }
        List<RowHolder> dataRows = rows.stream()
                .filter(item -> !isExample(item.row()))
                .toList();
        if (dataRows.isEmpty()) {
            throw new BusinessException("没有可导入的数据");
        }
        Map<String, Integer> assetNoCount = new HashMap<>();
        Map<String, Integer> serialCount = new HashMap<>();
        for (RowHolder holder : dataRows) {
            String assetNo = trim(holder.row().getAssetNo());
            if (assetNo != null) {
                assetNoCount.merge(assetNo, 1, Integer::sum);
            }
            String serial = trim(holder.row().getSerialNo());
            if (serial != null) {
                serialCount.merge(serial, 1, Integer::sum);
            }
        }

        AssetImportVO result = new AssetImportVO();
        for (RowHolder holder : dataRows) {
            try {
                saveRow(holder.row(), assetNoCount, serialCount);
                result.setSuccessCount(result.getSuccessCount() + 1);
            } catch (BusinessException ex) {
                AssetImportFailVO fail = new AssetImportFailVO();
                fail.setRowNum(holder.rowNum());
                fail.setAssetNo(trim(holder.row().getAssetNo()));
                fail.setReason(ex.getMessage());
                result.getFailures().add(fail);
            }
        }
        result.setFailCount(result.getFailures().size());
        if (result.getFailCount() > 0) {
            failFiles.put(operator.getUserId(), writeFailFile(result.getFailures()));
        } else {
            failFiles.remove(operator.getUserId());
        }
        audit(operator, "IMPORT", null,
                "导入成功 " + result.getSuccessCount() + " 行，失败 " + result.getFailCount() + " 行");
        return result;
    }

    @Override
    public byte[] lastFailFile(AuthUser operator) {
        byte[] bytes = failFiles.get(operator.getUserId());
        if (bytes == null || bytes.length == 0) {
            throw new BusinessException("没有可下载的失败明细");
        }
        return bytes;
    }

    @Override
    public byte[] exportAssets(AssetQuery query, AuthUser operator) {
        long total = assetService.count(query, operator);
        if (total <= 0) {
            throw new BusinessException("没有可导出的设备");
        }
        int maxRows = amsProperties.getExcel().getExportMaxRows();
        if (total > maxRows) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "导出超过上限，请缩小筛选范围");
        }
        List<AssetExportRow> rows = assetService.listAll(query, operator).stream()
                .map(this::toExportRow)
                .toList();
        audit(operator, "EXPORT", null, "导出 " + rows.size() + " 台设备");
        return write(AssetExportRow.class, "设备台账", rows);
    }

    private void saveRow(AssetImportRow row, Map<String, Integer> assetNoCount, Map<String, Integer> serialCount) {
        String assetNo = required(row.getAssetNo(), "资产编号不能为空");
        String name = required(row.getName(), "资产名称不能为空");
        String categoryName = required(row.getCategoryName(), "设备类型不能为空");
        if (assetNoCount.getOrDefault(assetNo, 0) > 1) {
            throw new BusinessException("文件内资产编号重复");
        }
        String serial = trim(row.getSerialNo());
        if (serial != null && serialCount.getOrDefault(serial, 0) > 1) {
            throw new BusinessException("文件内序列号重复");
        }
        AssetSaveDTO dto = new AssetSaveDTO();
        dto.setAssetNo(assetNo);
        dto.setName(name);
        dto.setCategoryId(resolveCategory(categoryName));
        dto.setBrand(trim(row.getBrand()));
        dto.setModel(trim(row.getModel()));
        dto.setSerialNo(serial);
        dto.setPurchaseDate(parseDate(row.getPurchaseDate(), "购置日期格式不合法"));
        dto.setPurchasePrice(parsePrice(row.getPurchasePrice()));
        dto.setSupplier(trim(row.getSupplier()));
        dto.setWarrantyUntil(parseDate(row.getWarrantyUntil(), "保修截止日期格式不合法"));
        dto.setDeptId(resolveDept(trim(row.getDeptName())));
        dto.setLocationId(resolveLocation(trim(row.getLocationName())));
        dto.setRemark(trim(row.getRemark()));
        assetService.save(dto);
    }

    private Long resolveCategory(String name) {
        AssetCategoryDO found = assetCategoryDao.selectOne(Wrappers.<AssetCategoryDO>lambdaQuery()
                .eq(AssetCategoryDO::getName, name)
                .last("LIMIT 1"));
        if (found == null) {
            throw new BusinessException("设备类型不存在");
        }
        if (found.getEnabled() == null || found.getEnabled() != 1) {
            throw new BusinessException("设备类型已停用");
        }
        return found.getId();
    }

    private Long resolveDept(String name) {
        if (name == null) {
            return null;
        }
        SysDeptDO found = sysDeptDao.selectOne(Wrappers.<SysDeptDO>lambdaQuery()
                .eq(SysDeptDO::getName, name)
                .last("LIMIT 1"));
        if (found == null) {
            throw new BusinessException("责任部门不存在");
        }
        if (found.getEnabled() == null || found.getEnabled() != 1) {
            throw new BusinessException("责任部门已停用");
        }
        return found.getId();
    }

    private Long resolveLocation(String name) {
        if (name == null) {
            return null;
        }
        SysLocationDO found = sysLocationDao.selectOne(Wrappers.<SysLocationDO>lambdaQuery()
                .eq(SysLocationDO::getName, name)
                .last("LIMIT 1"));
        if (found == null) {
            throw new BusinessException("存放地点不存在");
        }
        if (found.getEnabled() == null || found.getEnabled() != 1) {
            throw new BusinessException("存放地点已停用");
        }
        return found.getId();
    }

    private LocalDate parseDate(String raw, String message) {
        String text = trim(raw);
        if (text == null) {
            return null;
        }
        try {
            return LocalDate.parse(text);
        } catch (DateTimeParseException ignored) {
            try {
                return DateUtil.parse(text).toLocalDateTime().toLocalDate();
            } catch (Exception ex) {
                throw new BusinessException(message);
            }
        }
    }

    private BigDecimal parsePrice(String raw) {
        String text = trim(raw);
        if (text == null) {
            return null;
        }
        try {
            BigDecimal price = new BigDecimal(text.replace(",", ""));
            if (price.compareTo(BigDecimal.ZERO) < 0) {
                throw new BusinessException("购置价格格式不合法");
            }
            return price;
        } catch (NumberFormatException ex) {
            throw new BusinessException("购置价格格式不合法");
        }
    }

    private List<RowHolder> readRows(MultipartFile file) {
        CollectListener listener = new CollectListener();
        try (InputStream in = file.getInputStream()) {
            EasyExcel.read(in, AssetImportRow.class, listener)
                    .sheet()
                    .headRowNumber(1)
                    .doRead();
        } catch (BusinessException ex) {
            throw ex;
        } catch (ExcelAnalysisException | IOException ex) {
            Throwable cause = ex;
            while (cause != null) {
                if (cause instanceof BusinessException business) {
                    throw business;
                }
                cause = cause.getCause();
            }
            throw new BusinessException("整文件无法解析，未写入任何数据");
        }
        return listener.rows;
    }

    private boolean isExample(AssetImportRow row) {
        return "EXAMPLE".equalsIgnoreCase(trim(row.getAssetNo()));
    }

    private String required(String value, String message) {
        String text = trim(value);
        if (text == null) {
            throw new BusinessException(message);
        }
        return text;
    }

    private String trim(String value) {
        return StrUtil.trimToNull(value);
    }

    private AssetExportRow toExportRow(AssetVO asset) {
        AssetExportRow row = new AssetExportRow();
        row.setAssetNo(asset.getAssetNo());
        row.setName(asset.getName());
        row.setCategoryName(asset.getCategoryName());
        row.setBrand(asset.getBrand());
        row.setModel(asset.getModel());
        row.setSerialNo(asset.getSerialNo());
        row.setStatusLabel(asset.isOverdue() ? "已逾期" : asset.getStatusLabel());
        row.setPurchaseDate(stringify(asset.getPurchaseDate()));
        row.setPurchasePrice(asset.getPurchasePrice() == null ? null : asset.getPurchasePrice().toPlainString());
        row.setSupplier(asset.getSupplier());
        row.setWarrantyUntil(stringify(asset.getWarrantyUntil()));
        row.setDeptName(asset.getDeptName());
        row.setLocationName(asset.getLocationName());
        row.setHolderName(asset.getHolderName());
        row.setBorrowStartDate(stringify(asset.getBorrowStartDate()));
        row.setExpectedReturnDate(stringify(asset.getExpectedReturnDate()));
        row.setRemark(asset.getRemark());
        return row;
    }

    private String stringify(LocalDate date) {
        return date == null ? null : date.toString();
    }

    private byte[] writeFailFile(List<AssetImportFailVO> failures) {
        List<AssetFailRow> rows = new ArrayList<>();
        for (AssetImportFailVO fail : failures) {
            AssetFailRow row = new AssetFailRow();
            row.setRowNum(fail.getRowNum());
            row.setAssetNo(fail.getAssetNo());
            row.setReason(fail.getReason());
            rows.add(row);
        }
        return write(AssetFailRow.class, "失败明细", rows);
    }

    private <T> byte[] write(Class<T> head, String sheet, List<T> rows) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        EasyExcel.write(out, head).sheet(sheet).doWrite(rows);
        return out.toByteArray();
    }

    private void audit(AuthUser operator, String action, String objectNo, String summary) {
        AuditLogDO log = new AuditLogDO();
        log.setOperatorId(operator.getUserId());
        log.setModule("ASSET");
        log.setAction(action);
        log.setObjectNo(objectNo);
        log.setSummary(summary);
        auditLogDao.insert(log);
    }

    private record RowHolder(int rowNum, AssetImportRow row) {
    }

    private static class CollectListener extends AnalysisEventListener<AssetImportRow> {
        private final List<RowHolder> rows = new ArrayList<>();

        @Override
        public void invokeHeadMap(Map<Integer, String> headMap, AnalysisContext context) {
            Set<String> actual = new HashSet<>();
            for (String name : headMap.values()) {
                if (StrUtil.isNotBlank(name)) {
                    actual.add(name.trim());
                }
            }
            if (!actual.containsAll(IMPORT_HEADERS) || actual.size() != IMPORT_HEADERS.size()) {
                throw new BusinessException("表头不匹配");
            }
        }

        @Override
        public void invoke(AssetImportRow data, AnalysisContext context) {
            int rowNum = context.readRowHolder().getRowIndex() + 1;
            rows.add(new RowHolder(rowNum, data));
        }

        @Override
        public void doAfterAllAnalysed(AnalysisContext context) {
            // no-op
        }
    }
}
