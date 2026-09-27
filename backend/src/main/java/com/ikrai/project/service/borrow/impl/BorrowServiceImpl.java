package com.ikrai.project.service.borrow.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ikrai.project.common.AuthUser;
import com.ikrai.project.common.enums.AssetStatus;
import com.ikrai.project.common.enums.BorrowLogAction;
import com.ikrai.project.common.enums.BorrowOrderStatus;
import com.ikrai.project.common.exception.BusinessException;
import com.ikrai.project.common.exception.ConflictException;
import com.ikrai.project.common.exception.NotFoundException;
import com.ikrai.project.config.AmsProperties;
import com.ikrai.project.dao.AssetDao;
import com.ikrai.project.dao.BorrowLogDao;
import com.ikrai.project.dao.BorrowOrderDao;
import com.ikrai.project.dao.SysUserDao;
import com.ikrai.project.dataobject.AssetDO;
import com.ikrai.project.dataobject.BorrowLogDO;
import com.ikrai.project.dataobject.BorrowOrderDO;
import com.ikrai.project.dataobject.SysUserDO;
import com.ikrai.project.dto.BorrowCreateDTO;
import com.ikrai.project.manager.asset.AssetManager;
import com.ikrai.project.manager.borrow.BorrowManager;
import com.ikrai.project.query.BorrowQuery;
import com.ikrai.project.service.borrow.BorrowService;
import com.ikrai.project.vo.BorrowLogVO;
import com.ikrai.project.vo.BorrowOrderVO;
import com.ikrai.project.vo.BorrowTodoVO;
import com.ikrai.project.vo.PageVO;
import cn.hutool.core.util.StrUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Service
public class BorrowServiceImpl implements BorrowService {

    private final BorrowOrderDao borrowOrderDao;
    private final BorrowLogDao borrowLogDao;
    private final AssetDao assetDao;
    private final SysUserDao sysUserDao;
    private final AssetManager assetManager;
    private final BorrowManager borrowManager;
    private final AmsProperties amsProperties;

    public BorrowServiceImpl(BorrowOrderDao borrowOrderDao,
                             BorrowLogDao borrowLogDao,
                             AssetDao assetDao,
                             SysUserDao sysUserDao,
                             AssetManager assetManager,
                             BorrowManager borrowManager,
                             AmsProperties amsProperties) {
        this.borrowOrderDao = borrowOrderDao;
        this.borrowLogDao = borrowLogDao;
        this.assetDao = assetDao;
        this.sysUserDao = sysUserDao;
        this.assetManager = assetManager;
        this.borrowManager = borrowManager;
        this.amsProperties = amsProperties;
    }

    @Override
    @Transactional
    public BorrowOrderVO save(AuthUser applicant, BorrowCreateDTO dto) {
        AssetDO asset = assetManager.getById(dto.getAssetId());
        if (borrowManager.hasActiveOrder(asset.getId(), null)) {
            throw occupied(asset);
        }
        if (dto.isSubmit()) {
            assertCanSubmit(dto, asset);
        }
        BorrowOrderDO order = new BorrowOrderDO();
        order.setAssetId(asset.getId());
        order.setApplicantId(applicant.getUserId());
        order.setPurpose(blankToNull(dto.getPurpose()));
        order.setExpectedReturnDate(dto.getExpectedReturnDate());
        order.setRemark(blankToNull(dto.getRemark()));
        order.setStatus(dto.isSubmit() ? BorrowOrderStatus.PENDING.name() : BorrowOrderStatus.DRAFT.name());
        order.setVersion(0);
        borrowManager.insertNew(order);
        if (dto.isSubmit()) {
            assetManager.occupyOnSubmit(asset, order.getId());
            borrowManager.saveLog(order.getId(), BorrowLogAction.SUBMIT, applicant.getUserId(), null);
        }
        return toVo(borrowOrderDao.selectById(order.getId()), false);
    }

    @Override
    @Transactional
    public BorrowOrderVO submit(AuthUser applicant, Long orderId) {
        BorrowOrderDO order = requireOwn(applicant, orderId);
        if (!BorrowOrderStatus.DRAFT.name().equals(order.getStatus())) {
            throw new ConflictException();
        }
        AssetDO asset = assetManager.getById(order.getAssetId());
        BorrowCreateDTO dto = new BorrowCreateDTO();
        dto.setPurpose(order.getPurpose());
        dto.setExpectedReturnDate(order.getExpectedReturnDate());
        assertCanSubmit(dto, asset);
        if (borrowManager.hasActiveOrder(asset.getId(), order.getId())) {
            throw occupied(asset);
        }
        borrowManager.casUpdateStatus(order, BorrowOrderStatus.PENDING.name());
        assetManager.occupyOnSubmit(asset, order.getId());
        borrowManager.saveLog(order.getId(), BorrowLogAction.SUBMIT, applicant.getUserId(), null);
        return toVo(borrowOrderDao.selectById(order.getId()), false);
    }

    @Override
    @Transactional
    public BorrowOrderVO withdraw(AuthUser applicant, Long orderId) {
        BorrowOrderDO order = requireOwn(applicant, orderId);
        if (!BorrowOrderStatus.PENDING.name().equals(order.getStatus())) {
            throw new ConflictException();
        }
        AssetDO asset = assetManager.getById(order.getAssetId());
        borrowManager.casUpdateStatus(order, BorrowOrderStatus.WITHDRAWN.name());
        assetManager.release(asset);
        borrowManager.saveLog(order.getId(), BorrowLogAction.WITHDRAW, applicant.getUserId(), "申请人撤回");
        return toVo(borrowOrderDao.selectById(order.getId()), false);
    }

    @Override
    @Transactional
    public BorrowOrderVO approve(AuthUser admin, Long orderId) {
        BorrowOrderDO order = requirePending(orderId);
        AssetDO asset = assetManager.getById(order.getAssetId());
        if (!AssetStatus.PENDING.name().equals(asset.getStatus())) {
            throw new ConflictException();
        }
        borrowManager.casUpdateStatus(order, BorrowOrderStatus.APPROVED.name());
        BorrowOrderDO latest = borrowOrderDao.selectById(order.getId());
        latest.setApproverId(admin.getUserId());
        latest.setApprovedAt(LocalDateTime.now());
        borrowOrderDao.updateById(latest);
        borrowManager.saveLog(order.getId(), BorrowLogAction.APPROVE, admin.getUserId(), null);
        return toVo(borrowOrderDao.selectById(order.getId()), false);
    }

    @Override
    @Transactional
    public BorrowOrderVO reject(AuthUser admin, Long orderId, String comment) {
        if (StrUtil.isBlank(comment)) {
            throw new BusinessException("驳回必须填写原因");
        }
        BorrowOrderDO order = requirePending(orderId);
        AssetDO asset = assetManager.getById(order.getAssetId());
        borrowManager.casUpdateStatus(order, BorrowOrderStatus.REJECTED.name());
        BorrowOrderDO latest = borrowOrderDao.selectById(order.getId());
        latest.setApproverId(admin.getUserId());
        latest.setApprovedAt(LocalDateTime.now());
        latest.setApproveComment(comment.trim());
        borrowOrderDao.updateById(latest);
        assetManager.release(asset);
        borrowManager.saveLog(order.getId(), BorrowLogAction.REJECT, admin.getUserId(), comment.trim());
        return toVo(borrowOrderDao.selectById(order.getId()), false);
    }

    @Override
    @Transactional
    public BorrowOrderVO issue(AuthUser admin, Long orderId) {
        BorrowOrderDO order = borrowManager.getById(orderId);
        if (!BorrowOrderStatus.APPROVED.name().equals(order.getStatus())) {
            throw new ConflictException();
        }
        AssetDO asset = assetManager.getById(order.getAssetId());
        // 发放前设备必须仍是审批中，发放后才变为已领用
        if (!AssetStatus.PENDING.name().equals(asset.getStatus())) {
            throw new ConflictException();
        }
        borrowManager.casUpdateStatus(order, BorrowOrderStatus.BORROWING.name());
        BorrowOrderDO latest = borrowOrderDao.selectById(order.getId());
        LocalDateTime issuedAt = LocalDateTime.now();
        latest.setIssuedAt(issuedAt);
        borrowOrderDao.updateById(latest);
        assetManager.issue(asset, order.getApplicantId(), issuedAt.toLocalDate(), order.getExpectedReturnDate());
        borrowManager.saveLog(order.getId(), BorrowLogAction.ISSUE, admin.getUserId(), null);
        return toVo(borrowOrderDao.selectById(order.getId()), false);
    }

    @Override
    @Transactional
    public BorrowOrderVO requestReturn(AuthUser applicant, Long orderId) {
        BorrowOrderDO order = requireOwn(applicant, orderId);
        if (!BorrowOrderStatus.BORROWING.name().equals(order.getStatus())) {
            throw new ConflictException();
        }
        borrowManager.casUpdateStatus(order, BorrowOrderStatus.RETURN_PENDING.name());
        BorrowOrderDO latest = borrowOrderDao.selectById(order.getId());
        latest.setReturnRequestedAt(LocalDateTime.now());
        borrowOrderDao.updateById(latest);
        borrowManager.saveLog(order.getId(), BorrowLogAction.REQUEST_RETURN, applicant.getUserId(), null);
        return toVo(borrowOrderDao.selectById(order.getId()), false);
    }

    @Override
    @Transactional
    public BorrowOrderVO confirmReturn(AuthUser admin, Long orderId, String comment) {
        BorrowOrderDO order = borrowManager.getById(orderId);
        if (!BorrowOrderStatus.BORROWING.name().equals(order.getStatus())
                && !BorrowOrderStatus.RETURN_PENDING.name().equals(order.getStatus())) {
            throw new ConflictException();
        }
        AssetDO asset = assetManager.getById(order.getAssetId());
        borrowManager.casUpdateStatus(order, BorrowOrderStatus.RETURNED.name());
        BorrowOrderDO latest = borrowOrderDao.selectById(order.getId());
        latest.setReturnedAt(LocalDateTime.now());
        latest.setReturnComment(blankToNull(comment));
        borrowOrderDao.updateById(latest);
        assetManager.confirmReturn(asset);
        borrowManager.saveLog(order.getId(), BorrowLogAction.CONFIRM_RETURN, admin.getUserId(), blankToNull(comment));
        return toVo(borrowOrderDao.selectById(order.getId()), false);
    }

    @Override
    public BorrowOrderVO get(AuthUser viewer, Long orderId) {
        BorrowOrderDO order = borrowManager.getById(orderId);
        if (!viewer.isAdmin() && !Objects.equals(order.getApplicantId(), viewer.getUserId())) {
            throw new NotFoundException("领用单不存在");
        }
        return toVo(order, true);
    }

    @Override
    public PageVO<BorrowOrderVO> list(AuthUser viewer, BorrowQuery query) {
        int page = query.getPage() == null || query.getPage() < 1 ? 1 : query.getPage();
        int pageSize = query.getPageSize() == null ? 20 : Math.min(query.getPageSize(), 100);
        var wrapper = Wrappers.<BorrowOrderDO>lambdaQuery();
        if (!viewer.isAdmin()) {
            wrapper.eq(BorrowOrderDO::getApplicantId, viewer.getUserId());
        }
        if (StrUtil.isNotBlank(query.getStatus())) {
            wrapper.eq(BorrowOrderDO::getStatus, query.getStatus());
        }
        wrapper.orderByDesc(BorrowOrderDO::getUpdatedAt).orderByDesc(BorrowOrderDO::getId);
        Page<BorrowOrderDO> result = borrowOrderDao.selectPage(new Page<>(page, pageSize), wrapper);
        List<BorrowOrderVO> list = result.getRecords().stream()
                .map(item -> toVo(item, false))
                .toList();
        return new PageVO<>(list, result.getTotal(), page, pageSize);
    }

    @Override
    public BorrowTodoVO listTodos() {
        BorrowTodoVO vo = new BorrowTodoVO();
        vo.setPending(listByStatus(BorrowOrderStatus.PENDING));
        vo.setApproved(listByStatus(BorrowOrderStatus.APPROVED));
        vo.setReturnPending(listByStatus(BorrowOrderStatus.RETURN_PENDING));
        return vo;
    }

    private List<BorrowOrderVO> listByStatus(BorrowOrderStatus status) {
        return borrowOrderDao.selectList(Wrappers.<BorrowOrderDO>lambdaQuery()
                        .eq(BorrowOrderDO::getStatus, status.name())
                        .orderByDesc(BorrowOrderDO::getUpdatedAt))
                .stream()
                .map(item -> toVo(item, false))
                .toList();
    }

    private void assertCanSubmit(BorrowCreateDTO dto, AssetDO asset) {
        if (!AssetStatus.IN_STOCK.name().equals(asset.getStatus())) {
            throw occupied(asset);
        }
        if (StrUtil.isBlank(dto.getPurpose())) {
            throw new BusinessException("用途不能为空");
        }
        if (dto.getExpectedReturnDate() == null) {
            throw new BusinessException("预计归还日不能为空");
        }
        LocalDate today = LocalDate.now();
        if (!dto.getExpectedReturnDate().isAfter(today)) {
            throw new BusinessException("预计归还日必须晚于今天");
        }
        int maxDays = amsProperties.getBorrow().getMaxDays();
        if (dto.getExpectedReturnDate().isAfter(today.plusDays(maxDays))) {
            throw new BusinessException("预计归还日不能超过 " + maxDays + " 天");
        }
    }

    private BusinessException occupied(AssetDO asset) {
        String label;
        try {
            label = AssetStatus.valueOf(asset.getStatus()).getLabel();
        } catch (Exception ex) {
            label = asset.getStatus();
        }
        return new BusinessException("该设备当前为" + label + "，不能申请");
    }

    private BorrowOrderDO requireOwn(AuthUser applicant, Long orderId) {
        BorrowOrderDO order = borrowManager.getById(orderId);
        if (!Objects.equals(order.getApplicantId(), applicant.getUserId())) {
            throw new NotFoundException("领用单不存在");
        }
        return order;
    }

    private BorrowOrderDO requirePending(Long orderId) {
        BorrowOrderDO order = borrowManager.getById(orderId);
        if (!BorrowOrderStatus.PENDING.name().equals(order.getStatus())) {
            throw new ConflictException();
        }
        return order;
    }

    private BorrowOrderVO toVo(BorrowOrderDO order, boolean withLogs) {
        BorrowOrderVO vo = new BorrowOrderVO();
        vo.setId(order.getId());
        vo.setOrderNo(order.getOrderNo());
        vo.setAssetId(order.getAssetId());
        AssetDO asset = assetDao.selectById(order.getAssetId());
        if (asset != null) {
            vo.setAssetNo(asset.getAssetNo());
            vo.setAssetName(asset.getName());
        }
        vo.setApplicantId(order.getApplicantId());
        SysUserDO applicant = sysUserDao.selectById(order.getApplicantId());
        vo.setApplicantName(applicant == null ? null : applicant.getRealName());
        vo.setPurpose(order.getPurpose());
        vo.setExpectedReturnDate(order.getExpectedReturnDate());
        vo.setRemark(order.getRemark());
        vo.setStatus(order.getStatus());
        vo.setStatusLabel(orderStatusLabel(order.getStatus()));
        vo.setOverdue(BorrowOrderStatus.BORROWING.name().equals(order.getStatus())
                && order.getExpectedReturnDate() != null
                && order.getExpectedReturnDate().isBefore(LocalDate.now()));
        vo.setApproverId(order.getApproverId());
        vo.setApprovedAt(order.getApprovedAt());
        vo.setApproveComment(order.getApproveComment());
        vo.setIssuedAt(order.getIssuedAt());
        vo.setReturnRequestedAt(order.getReturnRequestedAt());
        vo.setReturnedAt(order.getReturnedAt());
        vo.setReturnComment(order.getReturnComment());
        vo.setVersion(order.getVersion());
        vo.setCreatedAt(order.getCreatedAt());
        if (withLogs) {
            vo.setLogs(listLogs(order.getId()));
        }
        return vo;
    }

    private List<BorrowLogVO> listLogs(Long borrowId) {
        return borrowLogDao.selectList(Wrappers.<BorrowLogDO>lambdaQuery()
                        .eq(BorrowLogDO::getBorrowId, borrowId)
                        .orderByAsc(BorrowLogDO::getCreatedAt))
                .stream()
                .map(log -> {
                    BorrowLogVO vo = new BorrowLogVO();
                    vo.setId(log.getId());
                    vo.setAction(log.getAction());
                    vo.setOperatorId(log.getOperatorId());
                    SysUserDO operator = sysUserDao.selectById(log.getOperatorId());
                    vo.setOperatorName(operator == null ? null : operator.getRealName());
                    vo.setComment(log.getComment());
                    vo.setCreatedAt(log.getCreatedAt());
                    return vo;
                })
                .toList();
    }

    private String orderStatusLabel(String status) {
        try {
            return BorrowOrderStatus.valueOf(status).getLabel();
        } catch (Exception ex) {
            return status;
        }
    }

    private String blankToNull(String value) {
        return StrUtil.trimToNull(value);
    }
}
