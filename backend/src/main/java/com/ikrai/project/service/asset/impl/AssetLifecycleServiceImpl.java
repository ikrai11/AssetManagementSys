package com.ikrai.project.service.asset.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.ikrai.project.common.AuthUser;
import com.ikrai.project.common.enums.AssetLogAction;
import com.ikrai.project.common.enums.AssetStatus;
import com.ikrai.project.common.exception.BusinessException;
import com.ikrai.project.common.exception.ConflictException;
import com.ikrai.project.dao.AssetLogDao;
import com.ikrai.project.dao.AssetRepairDao;
import com.ikrai.project.dao.AuditLogDao;
import com.ikrai.project.dao.SysDeptDao;
import com.ikrai.project.dao.SysLocationDao;
import com.ikrai.project.dataobject.AssetDO;
import com.ikrai.project.dataobject.AssetLogDO;
import com.ikrai.project.dataobject.AssetRepairDO;
import com.ikrai.project.dataobject.AuditLogDO;
import com.ikrai.project.dataobject.SysDeptDO;
import com.ikrai.project.dataobject.SysLocationDO;
import com.ikrai.project.dto.AssetRepairFinishDTO;
import com.ikrai.project.dto.AssetRepairStartDTO;
import com.ikrai.project.dto.AssetScrapDTO;
import com.ikrai.project.dto.AssetTransferDTO;
import com.ikrai.project.manager.asset.AssetManager;
import com.ikrai.project.service.asset.AssetLifecycleService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Objects;

@Service
public class AssetLifecycleServiceImpl implements AssetLifecycleService {

    private static final String REPAIR_OPEN = "OPEN";
    private static final String REPAIR_DONE = "DONE";
    private static final String REPAIR_CLOSED = "CLOSED";

    private final AssetManager assetManager;
    private final AssetLogDao assetLogDao;
    private final AssetRepairDao assetRepairDao;
    private final AuditLogDao auditLogDao;
    private final SysDeptDao sysDeptDao;
    private final SysLocationDao sysLocationDao;

    public AssetLifecycleServiceImpl(AssetManager assetManager,
                                     AssetLogDao assetLogDao,
                                     AssetRepairDao assetRepairDao,
                                     AuditLogDao auditLogDao,
                                     SysDeptDao sysDeptDao,
                                     SysLocationDao sysLocationDao) {
        this.assetManager = assetManager;
        this.assetLogDao = assetLogDao;
        this.assetRepairDao = assetRepairDao;
        this.auditLogDao = auditLogDao;
        this.sysDeptDao = sysDeptDao;
        this.sysLocationDao = sysLocationDao;
    }

    @Override
    @Transactional
    public void transfer(AuthUser operator, Long assetId, AssetTransferDTO dto) {
        String reason = requireText(dto.getReason(), "调拨原因不能为空");
        AssetDO asset = assetManager.getById(assetId);
        if (AssetStatus.SCRAPPED.name().equals(asset.getStatus())) {
            throw new BusinessException("已报废设备不可调拨");
        }
        Long deptId = resolveDept(dto.getDeptId(), asset.getDeptId());
        Long locationId = resolveLocation(dto.getLocationId(), asset.getLocationId());
        if (Objects.equals(deptId, asset.getDeptId()) && Objects.equals(locationId, asset.getLocationId())) {
            throw new BusinessException("责任部门与存放地点均未变化");
        }
        String comment = transferComment(reason, asset.getDeptId(), deptId, asset.getLocationId(), locationId);
        assetManager.relocate(asset, deptId, locationId);
        writeLog(asset.getId(), operator.getUserId(), AssetLogAction.TRANSFER, comment);
        writeAudit(operator, asset.getAssetNo(), AssetLogAction.TRANSFER.name(), comment);
    }

    @Override
    @Transactional
    public void startRepair(AuthUser operator, Long assetId, AssetRepairStartDTO dto) {
        String fault = requireText(dto.getFault(), "故障说明不能为空");
        AssetDO asset = assetManager.getById(assetId);
        if (AssetStatus.REPAIRING.name().equals(asset.getStatus())) {
            throw new ConflictException();
        }
        if (!AssetStatus.IN_STOCK.name().equals(asset.getStatus())) {
            throw cannotRepair(asset);
        }
        LocalDate sentDate = dto.getSentDate() == null ? LocalDate.now() : dto.getSentDate();
        if (sentDate.isAfter(LocalDate.now())) {
            throw new BusinessException("送修日不能晚于今天");
        }
        assetManager.changeStatus(asset, AssetStatus.IN_STOCK.name(), AssetStatus.REPAIRING.name());
        AssetRepairDO repair = new AssetRepairDO();
        repair.setAssetId(asset.getId());
        repair.setFault(fault);
        repair.setSentDate(sentDate);
        repair.setStatus(REPAIR_OPEN);
        repair.setOperatorId(operator.getUserId());
        assetRepairDao.insert(repair);
        String comment = "送修日 " + sentDate + "；故障：" + fault;
        writeLog(asset.getId(), operator.getUserId(), AssetLogAction.REPAIR_START, comment);
        writeAudit(operator, asset.getAssetNo(), AssetLogAction.REPAIR_START.name(), comment);
    }

    @Override
    @Transactional
    public void finishRepair(AuthUser operator, Long assetId, AssetRepairFinishDTO dto) {
        String result = requireText(dto.getResult(), "维修结果不能为空");
        AssetDO asset = assetManager.getById(assetId);
        if (!AssetStatus.REPAIRING.name().equals(asset.getStatus())) {
            throw new ConflictException();
        }
        AssetRepairDO repair = openRepair(assetId);
        if (repair == null) {
            throw new ConflictException();
        }
        LocalDate finishedDate = dto.getFinishedDate() == null ? LocalDate.now() : dto.getFinishedDate();
        if (finishedDate.isAfter(LocalDate.now())) {
            throw new BusinessException("完成日不能晚于今天");
        }
        if (finishedDate.isBefore(repair.getSentDate())) {
            throw new BusinessException("完成日不能早于送修日");
        }
        assetManager.changeStatus(asset, AssetStatus.REPAIRING.name(), AssetStatus.IN_STOCK.name());
        repair.setFinishedDate(finishedDate);
        repair.setResultText(result);
        repair.setStatus(REPAIR_DONE);
        assetRepairDao.updateById(repair);
        String comment = "完成日 " + finishedDate + "；结果：" + result;
        writeLog(asset.getId(), operator.getUserId(), AssetLogAction.REPAIR_FINISH, comment);
        writeAudit(operator, asset.getAssetNo(), AssetLogAction.REPAIR_FINISH.name(), comment);
    }

    @Override
    @Transactional
    public void scrap(AuthUser operator, Long assetId, AssetScrapDTO dto) {
        String reason = requireText(dto.getReason(), "报废原因不能为空");
        AssetDO asset = assetManager.getById(assetId);
        if (AssetStatus.SCRAPPED.name().equals(asset.getStatus())) {
            throw new ConflictException();
        }
        if (!AssetStatus.IN_STOCK.name().equals(asset.getStatus())
                && !AssetStatus.REPAIRING.name().equals(asset.getStatus())) {
            throw cannotScrap(asset);
        }
        String from = asset.getStatus();
        assetManager.changeStatus(asset, from, AssetStatus.SCRAPPED.name());
        closeOpenRepair(assetId);
        writeLog(asset.getId(), operator.getUserId(), AssetLogAction.SCRAP, reason);
        writeAudit(operator, asset.getAssetNo(), AssetLogAction.SCRAP.name(), reason);
    }

    private void closeOpenRepair(Long assetId) {
        AssetRepairDO repair = openRepair(assetId);
        if (repair == null) {
            return;
        }
        repair.setStatus(REPAIR_CLOSED);
        repair.setFinishedDate(LocalDate.now());
        if (StrUtil.isBlank(repair.getResultText())) {
            repair.setResultText("因报废终止");
        }
        assetRepairDao.updateById(repair);
    }

    private AssetRepairDO openRepair(Long assetId) {
        return assetRepairDao.selectOne(Wrappers.<AssetRepairDO>lambdaQuery()
                .eq(AssetRepairDO::getAssetId, assetId)
                .eq(AssetRepairDO::getStatus, REPAIR_OPEN)
                .orderByDesc(AssetRepairDO::getId)
                .last("LIMIT 1"));
    }

    private BusinessException cannotRepair(AssetDO asset) {
        if (AssetStatus.BORROWED.name().equals(asset.getStatus())) {
            return new BusinessException("该设备当前为" + AssetStatus.BORROWED.getLabel() + "，须先归还后再送修");
        }
        return new BusinessException("该设备当前为" + label(asset) + "，不能送修");
    }

    private BusinessException cannotScrap(AssetDO asset) {
        if (AssetStatus.BORROWED.name().equals(asset.getStatus())) {
            return new BusinessException("该设备当前为" + AssetStatus.BORROWED.getLabel() + "，须先归还后再报废");
        }
        return new BusinessException("该设备当前为" + label(asset) + "，不能报废");
    }

    private String label(AssetDO asset) {
        try {
            return AssetStatus.valueOf(asset.getStatus()).getLabel();
        } catch (Exception ex) {
            return asset.getStatus();
        }
    }

    private Long resolveDept(Long requested, Long current) {
        if (requested == null) {
            return current;
        }
        SysDeptDO dept = sysDeptDao.selectById(requested);
        if (dept == null || dept.getEnabled() == null || dept.getEnabled() != 1) {
            throw new BusinessException("部门不存在或已停用");
        }
        return requested;
    }

    private Long resolveLocation(Long requested, Long current) {
        if (requested == null) {
            return current;
        }
        SysLocationDO location = sysLocationDao.selectById(requested);
        if (location == null || location.getEnabled() == null || location.getEnabled() != 1) {
            throw new BusinessException("地点不存在或已停用");
        }
        return requested;
    }

    private String transferComment(String reason, Long oldDept, Long newDept, Long oldLocation, Long newLocation) {
        StringBuilder text = new StringBuilder(reason);
        if (!Objects.equals(oldDept, newDept)) {
            text.append("；部门：").append(deptName(oldDept)).append(" → ").append(deptName(newDept));
        }
        if (!Objects.equals(oldLocation, newLocation)) {
            text.append("；地点：").append(locationName(oldLocation)).append(" → ").append(locationName(newLocation));
        }
        return StrUtil.sub(text.toString(), 0, 500);
    }

    private String deptName(Long id) {
        if (id == null) {
            return "未指定";
        }
        SysDeptDO dept = sysDeptDao.selectById(id);
        return dept == null ? "未指定" : dept.getName();
    }

    private String locationName(Long id) {
        if (id == null) {
            return "未指定";
        }
        SysLocationDO location = sysLocationDao.selectById(id);
        return location == null ? "未指定" : location.getName();
    }

    private void writeLog(Long assetId, Long operatorId, AssetLogAction action, String comment) {
        AssetLogDO log = new AssetLogDO();
        log.setAssetId(assetId);
        log.setAction(action.name());
        log.setOperatorId(operatorId);
        log.setComment(comment);
        assetLogDao.insert(log);
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

    private String requireText(String value, String message) {
        String text = StrUtil.trimToNull(value);
        if (text == null) {
            throw new BusinessException(message);
        }
        return text;
    }
}
