package com.ikrai.project.service.asset.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ikrai.project.common.AuthUser;
import com.ikrai.project.common.enums.AssetStatus;
import com.ikrai.project.common.enums.BorrowOrderStatus;
import com.ikrai.project.common.exception.BusinessException;
import com.ikrai.project.common.exception.NotFoundException;
import com.ikrai.project.dao.AssetCategoryDao;
import com.ikrai.project.dao.AssetDao;
import com.ikrai.project.dao.BorrowLogDao;
import com.ikrai.project.dao.BorrowOrderDao;
import com.ikrai.project.dao.SysDeptDao;
import com.ikrai.project.dao.SysLocationDao;
import com.ikrai.project.dao.SysUserDao;
import com.ikrai.project.dataobject.AssetCategoryDO;
import com.ikrai.project.dataobject.AssetDO;
import com.ikrai.project.dataobject.BorrowLogDO;
import com.ikrai.project.dataobject.BorrowOrderDO;
import com.ikrai.project.dataobject.SysDeptDO;
import com.ikrai.project.dataobject.SysLocationDO;
import com.ikrai.project.dataobject.SysUserDO;
import com.ikrai.project.dto.AssetSaveDTO;
import com.ikrai.project.query.AssetQuery;
import com.ikrai.project.service.asset.AssetService;
import com.ikrai.project.vo.AssetDetailVO;
import com.ikrai.project.vo.AssetVO;
import com.ikrai.project.vo.BorrowLogVO;
import com.ikrai.project.vo.PageVO;
import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class AssetServiceImpl implements AssetService {

    private final AssetDao assetDao;
    private final AssetCategoryDao assetCategoryDao;
    private final SysDeptDao sysDeptDao;
    private final SysLocationDao sysLocationDao;
    private final SysUserDao sysUserDao;
    private final BorrowOrderDao borrowOrderDao;
    private final BorrowLogDao borrowLogDao;

    public AssetServiceImpl(AssetDao assetDao,
                            AssetCategoryDao assetCategoryDao,
                            SysDeptDao sysDeptDao,
                            SysLocationDao sysLocationDao,
                            SysUserDao sysUserDao,
                            BorrowOrderDao borrowOrderDao,
                            BorrowLogDao borrowLogDao) {
        this.assetDao = assetDao;
        this.assetCategoryDao = assetCategoryDao;
        this.sysDeptDao = sysDeptDao;
        this.sysLocationDao = sysLocationDao;
        this.sysUserDao = sysUserDao;
        this.borrowOrderDao = borrowOrderDao;
        this.borrowLogDao = borrowLogDao;
    }

    @Override
    @Transactional
    public AssetVO save(AssetSaveDTO dto) {
        assertUnique(dto.getAssetNo(), dto.getSerialNo(), null);
        AssetDO asset = new AssetDO();
        fill(asset, dto);
        asset.setStatus(AssetStatus.IN_STOCK.name());
        asset.setVersion(0);
        assetDao.insert(asset);
        return toVo(asset, true);
    }

    @Override
    @Transactional
    public AssetVO update(Long id, AssetSaveDTO dto) {
        AssetDO asset = requireAsset(id);
        assertUnique(dto.getAssetNo(), dto.getSerialNo(), id);
        fill(asset, dto);
        assetDao.updateById(asset);
        return toVo(assetDao.selectById(id), true);
    }

    @Override
    @Transactional
    public void remove(Long id) {
        AssetDO asset = requireAsset(id);
        if (!AssetStatus.IN_STOCK.name().equals(asset.getStatus())) {
            throw new BusinessException("仅在库且无领用记录的设备可删除");
        }
        Long count = borrowOrderDao.selectCount(Wrappers.<BorrowOrderDO>lambdaQuery()
                .eq(BorrowOrderDO::getAssetId, id));
        if (count != null && count > 0) {
            throw new BusinessException("仅在库且无领用记录的设备可删除");
        }
        assetDao.deleteById(id);
    }

    @Override
    public AssetDetailVO get(Long id, AuthUser viewer) {
        AssetDO asset = requireAsset(id);
        if (!visibleTo(asset, viewer)) {
            throw new NotFoundException("设备不存在");
        }
        AssetDetailVO vo = new AssetDetailVO();
        copy(toVo(asset, viewer.isAdmin()), vo);
        vo.setLogs(listLogs(id));
        return vo;
    }

    @Override
    public PageVO<AssetVO> list(AssetQuery query, AuthUser viewer) {
        validateDateRange(query.getBorrowStartFrom(), query.getBorrowStartTo());
        validateDateRange(query.getDueFrom(), query.getDueTo());
        int page = query.getPage() == null || query.getPage() < 1 ? 1 : query.getPage();
        int pageSize = query.getPageSize() == null ? 20 : Math.min(query.getPageSize(), 100);

        Page<AssetDO> result = assetDao.selectPage(new Page<>(page, pageSize), buildWrapper(query, viewer, true));
        List<AssetVO> list = result.getRecords().stream()
                .map(item -> toVo(item, viewer.isAdmin()))
                .toList();
        return new PageVO<>(list, result.getTotal(), page, pageSize);
    }

    @Override
    public long count(AssetQuery query, AuthUser viewer) {
        validateDateRange(query.getBorrowStartFrom(), query.getBorrowStartTo());
        validateDateRange(query.getDueFrom(), query.getDueTo());
        Long total = assetDao.selectCount(buildWrapper(query, viewer, false));
        return total == null ? 0 : total;
    }

    @Override
    public List<AssetVO> listAll(AssetQuery query, AuthUser viewer) {
        validateDateRange(query.getBorrowStartFrom(), query.getBorrowStartTo());
        validateDateRange(query.getDueFrom(), query.getDueTo());
        return assetDao.selectList(buildWrapper(query, viewer, true)).stream()
                .map(item -> toVo(item, viewer.isAdmin()))
                .toList();
    }

    private LambdaQueryWrapper<AssetDO> buildWrapper(AssetQuery query, AuthUser viewer, boolean ordered) {
        LambdaQueryWrapper<AssetDO> wrapper = Wrappers.lambdaQuery();
        if (query.getCategoryIds() != null && !query.getCategoryIds().isEmpty()) {
            wrapper.in(AssetDO::getCategoryId, query.getCategoryIds());
        }
        List<String> statuses = query.getStatuses() == null ? new ArrayList<>() : new ArrayList<>(query.getStatuses());
        boolean overdueOnly = statuses.remove("OVERDUE");
        if (!statuses.isEmpty()) {
            wrapper.in(AssetDO::getStatus, statuses);
        }
        if (overdueOnly) {
            wrapper.eq(AssetDO::getStatus, AssetStatus.BORROWED.name())
                    .lt(AssetDO::getExpectedReturnDate, LocalDate.now());
        }
        if (query.getBorrowStartFrom() != null) {
            wrapper.ge(AssetDO::getBorrowStartDate, query.getBorrowStartFrom());
        }
        if (query.getBorrowStartTo() != null) {
            wrapper.le(AssetDO::getBorrowStartDate, query.getBorrowStartTo());
        }
        if (query.getDueFrom() != null) {
            wrapper.ge(AssetDO::getExpectedReturnDate, query.getDueFrom());
        }
        if (query.getDueTo() != null) {
            wrapper.le(AssetDO::getExpectedReturnDate, query.getDueTo());
        }
        if (StrUtil.isNotBlank(query.getKeyword())) {
            String keyword = query.getKeyword().trim();
            wrapper.and(w -> w.like(AssetDO::getAssetNo, keyword)
                    .or().like(AssetDO::getName, keyword)
                    .or().like(AssetDO::getSerialNo, keyword));
        }
        if (viewer.isAdmin() && query.getHolderUserId() != null) {
            wrapper.eq(AssetDO::getHolderUserId, query.getHolderUserId());
        }
        if (query.getDeptId() != null) {
            wrapper.eq(AssetDO::getDeptId, query.getDeptId());
        }
        if (query.getLocationId() != null) {
            wrapper.eq(AssetDO::getLocationId, query.getLocationId());
        }
        if (!viewer.isAdmin()) {
            applyUserScope(wrapper, viewer.getUserId());
        }
        if (ordered) {
            wrapper.orderByDesc(AssetDO::getUpdatedAt).orderByDesc(AssetDO::getId);
        }
        return wrapper;
    }

    private void applyUserScope(LambdaQueryWrapper<AssetDO> wrapper, Long userId) {
        List<BorrowOrderDO> mine = borrowOrderDao.selectList(Wrappers.<BorrowOrderDO>lambdaQuery()
                .eq(BorrowOrderDO::getApplicantId, userId)
                .in(BorrowOrderDO::getStatus,
                        BorrowOrderStatus.DRAFT.name(),
                        BorrowOrderStatus.PENDING.name(),
                        BorrowOrderStatus.APPROVED.name(),
                        BorrowOrderStatus.BORROWING.name(),
                        BorrowOrderStatus.RETURN_PENDING.name()));
        Set<Long> myBorrowIds = mine.stream().map(BorrowOrderDO::getId).collect(Collectors.toSet());
        wrapper.and(w -> {
            w.eq(AssetDO::getStatus, AssetStatus.IN_STOCK.name());
            if (!myBorrowIds.isEmpty()) {
                w.or().in(AssetDO::getCurrentBorrowId, myBorrowIds);
            }
        });
    }

    private boolean visibleTo(AssetDO asset, AuthUser viewer) {
        if (viewer.isAdmin()) {
            return true;
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

    private void fill(AssetDO asset, AssetSaveDTO dto) {
        asset.setAssetNo(dto.getAssetNo().trim());
        asset.setName(dto.getName().trim());
        asset.setCategoryId(dto.getCategoryId());
        asset.setBrand(blankToNull(dto.getBrand()));
        asset.setModel(blankToNull(dto.getModel()));
        asset.setSerialNo(blankToNull(dto.getSerialNo()));
        asset.setPurchaseDate(dto.getPurchaseDate());
        asset.setPurchasePrice(dto.getPurchasePrice());
        asset.setSupplier(blankToNull(dto.getSupplier()));
        asset.setWarrantyUntil(dto.getWarrantyUntil());
        asset.setDeptId(dto.getDeptId());
        asset.setLocationId(dto.getLocationId());
        asset.setRemark(blankToNull(dto.getRemark()));
    }

    private void assertUnique(String assetNo, String serialNo, Long excludeId) {
        Long noCount = assetDao.selectCount(Wrappers.<AssetDO>lambdaQuery()
                .eq(AssetDO::getAssetNo, assetNo.trim())
                .ne(excludeId != null, AssetDO::getId, excludeId));
        if (noCount != null && noCount > 0) {
            throw new BusinessException("资产编号已存在");
        }
        String serial = blankToNull(serialNo);
        if (serial != null) {
            Long serialCount = assetDao.selectCount(Wrappers.<AssetDO>lambdaQuery()
                    .eq(AssetDO::getSerialNo, serial)
                    .ne(excludeId != null, AssetDO::getId, excludeId));
            if (serialCount != null && serialCount > 0) {
                throw new BusinessException("序列号已存在");
            }
        }
    }

    private void validateDateRange(LocalDate from, LocalDate to) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new BusinessException("开始日不得晚于结束日");
        }
    }

    private AssetDO requireAsset(Long id) {
        AssetDO asset = assetDao.selectById(id);
        if (asset == null) {
            throw new NotFoundException("设备不存在");
        }
        return asset;
    }

    private AssetVO toVo(AssetDO asset, boolean admin) {
        AssetVO vo = new AssetVO();
        vo.setId(asset.getId());
        vo.setAssetNo(asset.getAssetNo());
        vo.setName(asset.getName());
        vo.setCategoryId(asset.getCategoryId());
        AssetCategoryDO category = asset.getCategoryId() == null ? null : assetCategoryDao.selectById(asset.getCategoryId());
        vo.setCategoryName(category == null ? null : category.getName());
        vo.setBrand(asset.getBrand());
        vo.setModel(asset.getModel());
        vo.setSerialNo(asset.getSerialNo());
        vo.setStatus(asset.getStatus());
        vo.setStatusLabel(statusLabel(asset.getStatus()));
        vo.setPurchaseDate(asset.getPurchaseDate());
        vo.setWarrantyUntil(asset.getWarrantyUntil());
        vo.setDeptId(asset.getDeptId());
        SysDeptDO dept = asset.getDeptId() == null ? null : sysDeptDao.selectById(asset.getDeptId());
        vo.setDeptName(dept == null ? null : dept.getName());
        vo.setLocationId(asset.getLocationId());
        SysLocationDO location = asset.getLocationId() == null ? null : sysLocationDao.selectById(asset.getLocationId());
        vo.setLocationName(location == null ? null : location.getName());
        vo.setHolderUserId(asset.getHolderUserId());
        SysUserDO holder = asset.getHolderUserId() == null ? null : sysUserDao.selectById(asset.getHolderUserId());
        vo.setHolderName(holder == null ? null : holder.getRealName());
        vo.setBorrowStartDate(asset.getBorrowStartDate());
        vo.setExpectedReturnDate(asset.getExpectedReturnDate());
        vo.setCurrentBorrowId(asset.getCurrentBorrowId());
        vo.setRemark(asset.getRemark());
        vo.setVersion(asset.getVersion());
        vo.setUpdatedAt(asset.getUpdatedAt());
        vo.setOverdue(AssetStatus.BORROWED.name().equals(asset.getStatus())
                && asset.getExpectedReturnDate() != null
                && asset.getExpectedReturnDate().isBefore(LocalDate.now()));
        if (admin) {
            vo.setPurchasePrice(asset.getPurchasePrice());
            vo.setSupplier(asset.getSupplier());
        }
        return vo;
    }

    private List<BorrowLogVO> listLogs(Long assetId) {
        List<BorrowOrderDO> orders = borrowOrderDao.selectList(Wrappers.<BorrowOrderDO>lambdaQuery()
                .eq(BorrowOrderDO::getAssetId, assetId)
                .orderByDesc(BorrowOrderDO::getCreatedAt));
        if (orders.isEmpty()) {
            return List.of();
        }
        List<Long> orderIds = orders.stream().map(BorrowOrderDO::getId).toList();
        List<BorrowLogDO> logs = borrowLogDao.selectList(Wrappers.<BorrowLogDO>lambdaQuery()
                .in(BorrowLogDO::getBorrowId, orderIds)
                .orderByAsc(BorrowLogDO::getCreatedAt));
        Map<Long, SysUserDO> users = logs.stream()
                .map(BorrowLogDO::getOperatorId)
                .distinct()
                .map(sysUserDao::selectById)
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(SysUserDO::getId, Function.identity()));
        List<BorrowLogVO> result = new ArrayList<>();
        for (BorrowLogDO log : logs) {
            BorrowLogVO vo = new BorrowLogVO();
            vo.setId(log.getId());
            vo.setAction(log.getAction());
            vo.setOperatorId(log.getOperatorId());
            SysUserDO operator = users.get(log.getOperatorId());
            vo.setOperatorName(operator == null ? null : operator.getRealName());
            vo.setComment(log.getComment());
            vo.setCreatedAt(log.getCreatedAt());
            result.add(vo);
        }
        return result;
    }

    private void copy(AssetVO source, AssetDetailVO target) {
        BeanUtil.copyProperties(source, target);
    }

    private String statusLabel(String status) {
        try {
            return AssetStatus.valueOf(status).getLabel();
        } catch (Exception ex) {
            return status;
        }
    }

    private String blankToNull(String value) {
        return StrUtil.trimToNull(value);
    }
}
