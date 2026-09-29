package com.ikrai.project.service.stocktake.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.ikrai.project.common.AuthUser;
import com.ikrai.project.common.enums.AssetLogAction;
import com.ikrai.project.common.enums.AssetStatus;
import com.ikrai.project.common.enums.StocktakeResult;
import com.ikrai.project.common.enums.StocktakeScope;
import com.ikrai.project.common.enums.StocktakeStatus;
import com.ikrai.project.common.exception.BusinessException;
import com.ikrai.project.common.exception.ConflictException;
import com.ikrai.project.common.exception.NotFoundException;
import com.ikrai.project.dao.AssetDao;
import com.ikrai.project.dao.AssetLogDao;
import com.ikrai.project.dao.AuditLogDao;
import com.ikrai.project.dao.StocktakeDao;
import com.ikrai.project.dao.StocktakeItemDao;
import com.ikrai.project.dao.SysDeptDao;
import com.ikrai.project.dao.SysLocationDao;
import com.ikrai.project.dataobject.AssetDO;
import com.ikrai.project.dataobject.AssetLogDO;
import com.ikrai.project.dataobject.AuditLogDO;
import com.ikrai.project.dataobject.StocktakeDO;
import com.ikrai.project.dataobject.StocktakeItemDO;
import com.ikrai.project.dataobject.SysDeptDO;
import com.ikrai.project.dataobject.SysLocationDO;
import com.ikrai.project.dto.StocktakeCreateDTO;
import com.ikrai.project.dto.StocktakeMarkDTO;
import com.ikrai.project.service.stocktake.StocktakeService;
import com.ikrai.project.vo.StocktakeItemVO;
import com.ikrai.project.vo.StocktakeVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class StocktakeServiceImpl implements StocktakeService {

    private final StocktakeDao stocktakeDao;
    private final StocktakeItemDao stocktakeItemDao;
    private final AssetDao assetDao;
    private final AssetLogDao assetLogDao;
    private final AuditLogDao auditLogDao;
    private final SysDeptDao sysDeptDao;
    private final SysLocationDao sysLocationDao;

    public StocktakeServiceImpl(StocktakeDao stocktakeDao,
                                StocktakeItemDao stocktakeItemDao,
                                AssetDao assetDao,
                                AssetLogDao assetLogDao,
                                AuditLogDao auditLogDao,
                                SysDeptDao sysDeptDao,
                                SysLocationDao sysLocationDao) {
        this.stocktakeDao = stocktakeDao;
        this.stocktakeItemDao = stocktakeItemDao;
        this.assetDao = assetDao;
        this.assetLogDao = assetLogDao;
        this.auditLogDao = auditLogDao;
        this.sysDeptDao = sysDeptDao;
        this.sysLocationDao = sysLocationDao;
    }

    @Override
    @Transactional
    public StocktakeVO create(AuthUser operator, StocktakeCreateDTO dto) {
        StocktakeScope scope = parseScope(dto.getScopeType());
        Long deptId = null;
        Long locationId = null;
        String scopeName;
        if (scope == StocktakeScope.DEPT) {
            if (dto.getLocationId() != null) {
                throw new BusinessException("按部门盘点时不能同时指定地点");
            }
            if (dto.getDeptId() == null) {
                throw new BusinessException("请选择部门");
            }
            SysDeptDO dept = requireEnabledDept(dto.getDeptId());
            deptId = dept.getId();
            scopeName = dept.getName();
        } else {
            if (dto.getDeptId() != null) {
                throw new BusinessException("按地点盘点时不能同时指定部门");
            }
            if (dto.getLocationId() == null) {
                throw new BusinessException("请选择地点");
            }
            SysLocationDO location = requireEnabledLocation(dto.getLocationId());
            locationId = location.getId();
            scopeName = location.getName();
        }
        List<AssetDO> assets = assetDao.selectList(Wrappers.<AssetDO>lambdaQuery()
                .eq(deptId != null, AssetDO::getDeptId, deptId)
                .eq(locationId != null, AssetDO::getLocationId, locationId)
                .ne(AssetDO::getStatus, AssetStatus.SCRAPPED.name())
                .orderByAsc(AssetDO::getAssetNo));
        StocktakeDO task = new StocktakeDO();
        task.setTitle(scopeName + "盘点 " + LocalDate.now());
        task.setScopeType(scope.name());
        task.setDeptId(deptId);
        task.setLocationId(locationId);
        task.setStatus(StocktakeStatus.OPEN.name());
        task.setOperatorId(operator.getUserId());
        stocktakeDao.insert(task);
        for (AssetDO asset : assets) {
            StocktakeItemDO item = new StocktakeItemDO();
            item.setStocktakeId(task.getId());
            item.setAssetId(asset.getId());
            item.setAssetNo(asset.getAssetNo());
            item.setAssetName(asset.getName());
            item.setAssetStatus(asset.getStatus());
            item.setDeptId(asset.getDeptId());
            item.setLocationId(asset.getLocationId());
            item.setResult(StocktakeResult.PENDING.name());
            stocktakeItemDao.insert(item);
        }
        writeAudit(operator, String.valueOf(task.getId()), "CREATE",
                "创建盘点：" + scopeName + "，应盘 " + assets.size() + " 台");
        return toVo(task, true);
    }

    @Override
    public List<StocktakeVO> list() {
        return stocktakeDao.selectList(Wrappers.<StocktakeDO>lambdaQuery()
                        .orderByDesc(StocktakeDO::getCreatedAt)
                        .orderByDesc(StocktakeDO::getId))
                .stream()
                .map(task -> toVo(task, false))
                .toList();
    }

    @Override
    public StocktakeVO get(Long id) {
        return toVo(requireTask(id), true);
    }

    @Override
    @Transactional
    public StocktakeVO mark(AuthUser operator, Long stocktakeId, Long itemId, StocktakeMarkDTO dto) {
        StocktakeDO task = requireTask(stocktakeId);
        if (!StocktakeStatus.OPEN.name().equals(task.getStatus())) {
            throw new ConflictException();
        }
        StocktakeResult result = parseResult(dto.getResult());
        String comment = StrUtil.trimToNull(dto.getComment());
        if (result == StocktakeResult.MISMATCH && comment == null) {
            throw new BusinessException("位置不符必须填写发现地点");
        }
        StocktakeItemDO item = stocktakeItemDao.selectById(itemId);
        if (item == null || !task.getId().equals(item.getStocktakeId())) {
            throw new NotFoundException("盘点明细不存在");
        }
        AssetDO before = assetDao.selectById(item.getAssetId());
        stocktakeItemDao.update(null, Wrappers.<StocktakeItemDO>lambdaUpdate()
                .eq(StocktakeItemDO::getId, item.getId())
                .set(StocktakeItemDO::getResult, result.name())
                .set(StocktakeItemDO::getComment, comment));
        AssetDO after = assetDao.selectById(item.getAssetId());
        if (before != null && after != null
                && (!before.getStatus().equals(after.getStatus())
                || !same(before.getHolderUserId(), after.getHolderUserId())
                || !same(before.getCurrentBorrowId(), after.getCurrentBorrowId())
                || !same(before.getLocationId(), after.getLocationId())
                || !same(before.getDeptId(), after.getDeptId()))) {
            throw new BusinessException("盘点不能改变设备状态或领用关系");
        }
        String summary = task.getTitle() + "：" + result.getLabel() + (comment == null ? "" : "，" + comment);
        AssetLogDO log = new AssetLogDO();
        log.setAssetId(item.getAssetId());
        log.setAction(AssetLogAction.STOCKTAKE.name());
        log.setOperatorId(operator.getUserId());
        log.setComment(StrUtil.sub(summary, 0, 500));
        assetLogDao.insert(log);
        writeAudit(operator, item.getAssetNo(), "MARK", summary);
        return toVo(requireTask(stocktakeId), true);
    }

    @Override
    @Transactional
    public StocktakeVO finish(AuthUser operator, Long id) {
        StocktakeDO task = requireTask(id);
        if (!StocktakeStatus.OPEN.name().equals(task.getStatus())) {
            throw new ConflictException();
        }
        Long pending = stocktakeItemDao.selectCount(Wrappers.<StocktakeItemDO>lambdaQuery()
                .eq(StocktakeItemDO::getStocktakeId, id)
                .eq(StocktakeItemDO::getResult, StocktakeResult.PENDING.name()));
        if (pending != null && pending > 0) {
            throw new BusinessException("还有 " + pending + " 台设备未盘点");
        }
        int rows = stocktakeDao.finish(id, LocalDateTime.now());
        if (rows == 0) {
            throw new ConflictException();
        }
        long difference = countDifference(id);
        writeAudit(operator, String.valueOf(id), "FINISH",
                "结束盘点：" + task.getTitle() + "，差异 " + difference + " 台");
        return toVo(requireTask(id), true);
    }

    private boolean same(Long left, Long right) {
        return left == null ? right == null : left.equals(right);
    }

    private StocktakeScope parseScope(String scopeType) {
        if (StrUtil.isBlank(scopeType)) {
            throw new BusinessException("请选择按部门或按地点盘点");
        }
        try {
            return StocktakeScope.valueOf(scopeType.trim());
        } catch (IllegalArgumentException ex) {
            throw new BusinessException("请选择按部门或按地点盘点");
        }
    }

    private StocktakeResult parseResult(String result) {
        if (StrUtil.isBlank(result)) {
            throw new BusinessException("请选择盘点结果");
        }
        try {
            StocktakeResult value = StocktakeResult.valueOf(result.trim());
            if (value == StocktakeResult.PENDING) {
                throw new BusinessException("盘点结果不合法");
            }
            return value;
        } catch (IllegalArgumentException ex) {
            throw new BusinessException("盘点结果不合法");
        }
    }

    private SysDeptDO requireEnabledDept(Long id) {
        SysDeptDO dept = sysDeptDao.selectById(id);
        if (dept == null || dept.getEnabled() == null || dept.getEnabled() != 1) {
            throw new BusinessException("部门不存在或已停用");
        }
        return dept;
    }

    private SysLocationDO requireEnabledLocation(Long id) {
        SysLocationDO location = sysLocationDao.selectById(id);
        if (location == null || location.getEnabled() == null || location.getEnabled() != 1) {
            throw new BusinessException("地点不存在或已停用");
        }
        return location;
    }

    private StocktakeDO requireTask(Long id) {
        StocktakeDO task = stocktakeDao.selectById(id);
        if (task == null) {
            throw new NotFoundException("盘点任务不存在");
        }
        return task;
    }

    private long countDifference(Long stocktakeId) {
        Long count = stocktakeItemDao.selectCount(Wrappers.<StocktakeItemDO>lambdaQuery()
                .eq(StocktakeItemDO::getStocktakeId, stocktakeId)
                .in(StocktakeItemDO::getResult, StocktakeResult.MISSING.name(), StocktakeResult.MISMATCH.name()));
        return count == null ? 0 : count;
    }

    private StocktakeVO toVo(StocktakeDO task, boolean withItems) {
        List<StocktakeItemDO> items = stocktakeItemDao.selectList(Wrappers.<StocktakeItemDO>lambdaQuery()
                .eq(StocktakeItemDO::getStocktakeId, task.getId())
                .orderByAsc(StocktakeItemDO::getAssetNo));
        StocktakeVO vo = new StocktakeVO();
        vo.setId(task.getId());
        vo.setTitle(task.getTitle());
        vo.setScopeType(task.getScopeType());
        vo.setDeptId(task.getDeptId());
        vo.setLocationId(task.getLocationId());
        vo.setScopeLabel(scopeLabel(task));
        vo.setStatus(task.getStatus());
        vo.setStatusLabel(StocktakeStatus.valueOf(task.getStatus()).getLabel());
        vo.setTotal(items.size());
        vo.setPending((int) items.stream().filter(item -> StocktakeResult.PENDING.name().equals(item.getResult())).count());
        vo.setDifference((int) items.stream().filter(this::isDifference).count());
        vo.setCreatedAt(task.getCreatedAt());
        vo.setFinishedAt(task.getFinishedAt());
        if (withItems) {
            vo.setItems(items.stream().map(this::toItem).toList());
        }
        return vo;
    }

    private boolean isDifference(StocktakeItemDO item) {
        return StocktakeResult.MISSING.name().equals(item.getResult())
                || StocktakeResult.MISMATCH.name().equals(item.getResult());
    }

    private String scopeLabel(StocktakeDO task) {
        if (StocktakeScope.DEPT.name().equals(task.getScopeType())) {
            SysDeptDO dept = task.getDeptId() == null ? null : sysDeptDao.selectById(task.getDeptId());
            return "部门：" + (dept == null ? "未指定" : dept.getName());
        }
        SysLocationDO location = task.getLocationId() == null ? null : sysLocationDao.selectById(task.getLocationId());
        return "地点：" + (location == null ? "未指定" : location.getName());
    }

    private StocktakeItemVO toItem(StocktakeItemDO item) {
        StocktakeItemVO vo = new StocktakeItemVO();
        vo.setId(item.getId());
        vo.setAssetId(item.getAssetId());
        vo.setAssetNo(item.getAssetNo());
        vo.setAssetName(item.getAssetName());
        vo.setAssetStatus(item.getAssetStatus());
        vo.setAssetStatusLabel(statusLabel(item.getAssetStatus()));
        SysDeptDO dept = item.getDeptId() == null ? null : sysDeptDao.selectById(item.getDeptId());
        vo.setDeptName(dept == null ? null : dept.getName());
        SysLocationDO location = item.getLocationId() == null ? null : sysLocationDao.selectById(item.getLocationId());
        vo.setLocationName(location == null ? null : location.getName());
        vo.setResult(item.getResult());
        vo.setResultLabel(StocktakeResult.valueOf(item.getResult()).getLabel());
        vo.setComment(item.getComment());
        return vo;
    }

    private String statusLabel(String status) {
        try {
            return AssetStatus.valueOf(status).getLabel();
        } catch (Exception ex) {
            return status;
        }
    }

    private void writeAudit(AuthUser operator, String objectNo, String action, String summary) {
        AuditLogDO log = new AuditLogDO();
        log.setOperatorId(operator.getUserId());
        log.setModule("STOCKTAKE");
        log.setAction(action);
        log.setObjectNo(objectNo);
        log.setSummary(StrUtil.sub(summary, 0, 500));
        auditLogDao.insert(log);
    }
}
