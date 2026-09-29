package com.ikrai.project.service.audit.impl;

import cn.hutool.core.util.StrUtil;
import com.alibaba.excel.EasyExcel;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ikrai.project.common.AuthUser;
import com.ikrai.project.common.exception.BusinessException;
import com.ikrai.project.config.AmsProperties;
import com.ikrai.project.dao.AuditLogDao;
import com.ikrai.project.dao.SysUserDao;
import com.ikrai.project.dataobject.AuditLogDO;
import com.ikrai.project.dataobject.SysUserDO;
import com.ikrai.project.excel.AuditExportRow;
import com.ikrai.project.query.AuditQuery;
import com.ikrai.project.service.audit.AuditService;
import com.ikrai.project.vo.AuditLogVO;
import com.ikrai.project.vo.PageVO;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class AuditServiceImpl implements AuditService {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final AuditLogDao auditLogDao;
    private final SysUserDao sysUserDao;
    private final AmsProperties amsProperties;

    public AuditServiceImpl(AuditLogDao auditLogDao, SysUserDao sysUserDao, AmsProperties amsProperties) {
        this.auditLogDao = auditLogDao;
        this.sysUserDao = sysUserDao;
        this.amsProperties = amsProperties;
    }

    @Override
    public PageVO<AuditLogVO> list(AuditQuery query, AuthUser operator) {
        requireAdmin(operator);
        validate(query);
        int page = query.getPage() == null || query.getPage() < 1 ? 1 : query.getPage();
        int pageSize = query.getPageSize() == null ? 20 : Math.min(Math.max(query.getPageSize(), 1), 100);
        List<Long> operatorIds = resolveOperators(query.getOperator());
        if (operatorIds != null && operatorIds.isEmpty()) {
            return new PageVO<>(List.of(), 0, page, pageSize);
        }
        Page<AuditLogDO> result = auditLogDao.selectPage(new Page<>(page, pageSize), filter(query, operatorIds, true));
        return new PageVO<>(toVos(result.getRecords()), result.getTotal(), page, pageSize);
    }

    @Override
    @Transactional
    public byte[] export(AuditQuery query, AuthUser operator) {
        requireAdmin(operator);
        validate(query);
        List<Long> operatorIds = resolveOperators(query.getOperator());
        if (operatorIds != null && operatorIds.isEmpty()) {
            throw new BusinessException("没有可导出的审计日志");
        }
        long total = auditLogDao.selectCount(filter(query, operatorIds, false));
        if (total <= 0) {
            throw new BusinessException("没有可导出的审计日志");
        }
        int maxRows = amsProperties.getExcel().getExportMaxRows();
        if (total > maxRows) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "导出超过上限，请缩小筛选范围");
        }
        List<AuditLogVO> rows = toVos(auditLogDao.selectList(filter(query, operatorIds, true)));
        byte[] bytes = write(rows);
        AuditLogDO log = new AuditLogDO();
        log.setOperatorId(operator.getUserId());
        log.setModule("AUDIT");
        log.setAction("EXPORT");
        log.setSummary("导出 " + rows.size() + " 条审计日志");
        auditLogDao.insert(log);
        return bytes;
    }

    private void requireAdmin(AuthUser operator) {
        if (operator == null || !operator.isAdmin()) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "无权限");
        }
    }

    private void validate(AuditQuery query) {
        if (query.getFrom() != null && query.getTo() != null && query.getFrom().isAfter(query.getTo())) {
            throw new BusinessException("开始日不得晚于结束日");
        }
    }

    /**
     * @return null 表示不按操作者过滤；空列表表示没有匹配的人
     */
    private List<Long> resolveOperators(String keyword) {
        if (StrUtil.isBlank(keyword)) {
            return null;
        }
        String text = keyword.trim();
        return sysUserDao.selectList(Wrappers.<SysUserDO>lambdaQuery()
                        .and(w -> w.like(SysUserDO::getRealName, text).or().like(SysUserDO::getUsername, text)))
                .stream()
                .map(SysUserDO::getId)
                .toList();
    }

    private LambdaQueryWrapper<AuditLogDO> filter(AuditQuery query, List<Long> operatorIds, boolean ordered) {
        LambdaQueryWrapper<AuditLogDO> wrapper = Wrappers.lambdaQuery();
        if (query.getFrom() != null) {
            wrapper.ge(AuditLogDO::getCreatedAt, query.getFrom().atStartOfDay());
        }
        if (query.getTo() != null) {
            wrapper.lt(AuditLogDO::getCreatedAt, query.getTo().plusDays(1).atStartOfDay());
        }
        if (StrUtil.isNotBlank(query.getModule())) {
            wrapper.eq(AuditLogDO::getModule, query.getModule().trim());
        }
        if (StrUtil.isNotBlank(query.getObjectNo())) {
            wrapper.like(AuditLogDO::getObjectNo, query.getObjectNo().trim());
        }
        if (operatorIds != null) {
            wrapper.in(AuditLogDO::getOperatorId, operatorIds);
        }
        if (ordered) {
            wrapper.orderByDesc(AuditLogDO::getCreatedAt).orderByDesc(AuditLogDO::getId);
        }
        return wrapper;
    }

    private List<AuditLogVO> toVos(List<AuditLogDO> logs) {
        Map<Long, SysUserDO> users = usersOf(logs.stream().map(AuditLogDO::getOperatorId).collect(Collectors.toSet()));
        return logs.stream().map(log -> toVo(log, users.get(log.getOperatorId()))).toList();
    }

    private Map<Long, SysUserDO> usersOf(Collection<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return sysUserDao.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(SysUserDO::getId, user -> user));
    }

    private AuditLogVO toVo(AuditLogDO log, SysUserDO user) {
        AuditLogVO vo = new AuditLogVO();
        vo.setId(log.getId());
        vo.setOperatorId(log.getOperatorId());
        vo.setOperatorName(user == null ? "未知" : user.getRealName());
        vo.setModule(log.getModule());
        vo.setModuleLabel(moduleLabel(log.getModule()));
        vo.setAction(log.getAction());
        vo.setActionLabel(actionLabel(log.getAction()));
        vo.setObjectNo(log.getObjectNo());
        vo.setSummary(log.getSummary());
        vo.setCreatedAt(log.getCreatedAt());
        return vo;
    }

    private byte[] write(List<AuditLogVO> rows) {
        List<AuditExportRow> excelRows = rows.stream().map(vo -> {
            AuditExportRow row = new AuditExportRow();
            row.setCreatedAt(vo.getCreatedAt() == null ? "" : vo.getCreatedAt().format(TIME));
            row.setOperatorName(vo.getOperatorName());
            row.setModuleLabel(vo.getModuleLabel());
            row.setActionLabel(vo.getActionLabel());
            row.setObjectNo(vo.getObjectNo());
            row.setSummary(vo.getSummary());
            return row;
        }).toList();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        EasyExcel.write(out, AuditExportRow.class).sheet("审计日志").doWrite(excelRows);
        return out.toByteArray();
    }

    static String moduleLabel(String module) {
        if (module == null) {
            return "";
        }
        return switch (module) {
            case "ASSET" -> "设备";
            case "PARAM" -> "系统参数";
            case "DEPT" -> "部门";
            case "LOCATION" -> "地点";
            case "STOCKTAKE" -> "盘点";
            case "AUDIT" -> "审计";
            default -> module;
        };
    }

    static String actionLabel(String action) {
        if (action == null) {
            return "";
        }
        return switch (action) {
            case "CREATE" -> "新增";
            case "UPDATE" -> "修改";
            case "DELETE" -> "删除";
            case "IMPORT" -> "导入";
            case "EXPORT" -> "导出";
            case "TRANSFER" -> "调拨";
            case "REPAIR_START" -> "送修";
            case "REPAIR_FINISH" -> "维修完成";
            case "SCRAP" -> "报废";
            case "MARK" -> "标记";
            case "FINISH" -> "结束";
            default -> action;
        };
    }
}
