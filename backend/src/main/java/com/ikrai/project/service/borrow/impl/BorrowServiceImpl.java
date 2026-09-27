package com.ikrai.project.service.borrow.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ikrai.project.common.AuthUser;
import com.ikrai.project.common.enums.AssetStatus;
import com.ikrai.project.common.enums.BorrowLogAction;
import com.ikrai.project.common.enums.BorrowOrderStatus;
import com.ikrai.project.common.enums.MessageType;
import com.ikrai.project.common.enums.RenewStatus;
import com.ikrai.project.common.exception.BusinessException;
import com.ikrai.project.common.exception.ConflictException;
import com.ikrai.project.common.exception.NotFoundException;
import com.ikrai.project.dao.AssetDao;
import com.ikrai.project.dao.BorrowLogDao;
import com.ikrai.project.dao.BorrowOrderDao;
import com.ikrai.project.dao.BorrowRenewDao;
import com.ikrai.project.dao.SysUserDao;
import com.ikrai.project.dataobject.AssetDO;
import com.ikrai.project.dataobject.BorrowLogDO;
import com.ikrai.project.dataobject.BorrowOrderDO;
import com.ikrai.project.dataobject.BorrowRenewDO;
import com.ikrai.project.dataobject.SysUserDO;
import com.ikrai.project.dto.BorrowCreateDTO;
import com.ikrai.project.dto.RenewApplyDTO;
import com.ikrai.project.manager.asset.AssetManager;
import com.ikrai.project.manager.borrow.BorrowManager;
import com.ikrai.project.manager.message.MessageManager;
import com.ikrai.project.query.BorrowQuery;
import com.ikrai.project.service.borrow.BorrowService;
import com.ikrai.project.service.system.SysParamService;
import com.ikrai.project.vo.BorrowLogVO;
import com.ikrai.project.vo.BorrowOrderVO;
import com.ikrai.project.vo.BorrowRenewVO;
import com.ikrai.project.vo.BorrowTodoVO;
import com.ikrai.project.vo.PageVO;
import cn.hutool.core.util.StrUtil;
import org.springframework.http.HttpStatus;
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
    private final BorrowRenewDao borrowRenewDao;
    private final SysParamService sysParamService;
    private final MessageManager messageManager;

    public BorrowServiceImpl(BorrowOrderDao borrowOrderDao,
                             BorrowLogDao borrowLogDao,
                             AssetDao assetDao,
                             SysUserDao sysUserDao,
                             AssetManager assetManager,
                             BorrowManager borrowManager,
                             BorrowRenewDao borrowRenewDao,
                             SysParamService sysParamService,
                             MessageManager messageManager) {
        this.borrowOrderDao = borrowOrderDao;
        this.borrowLogDao = borrowLogDao;
        this.assetDao = assetDao;
        this.sysUserDao = sysUserDao;
        this.assetManager = assetManager;
        this.borrowManager = borrowManager;
        this.borrowRenewDao = borrowRenewDao;
        this.sysParamService = sysParamService;
        this.messageManager = messageManager;
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
            notifyPending(order, asset, applicant);
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
        notifyPending(order, asset, applicant);
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
        notifyApplicant(order, asset, MessageType.APPROVAL_RESULT, "领用申请已通过",
                "单号：" + order.getOrderNo() + "\n设备：" + assetText(asset) + "\n请等待管理员发放。");
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
        notifyApplicant(order, asset, MessageType.APPROVAL_RESULT, "领用申请已驳回",
                "单号：" + order.getOrderNo() + "\n设备：" + assetText(asset) + "\n原因：" + comment.trim());
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
        notifyApplicant(order, asset, MessageType.ISSUE, "设备已发放",
                "单号：" + order.getOrderNo() + "\n设备：" + assetText(asset)
                        + "\n预计归还日：" + order.getExpectedReturnDate());
        return toVo(borrowOrderDao.selectById(order.getId()), false);
    }

    @Override
    @Transactional
    public BorrowOrderVO requestReturn(AuthUser applicant, Long orderId) {
        BorrowOrderDO order = requireOwn(applicant, orderId);
        if (!BorrowOrderStatus.BORROWING.name().equals(order.getStatus())) {
            throw new ConflictException();
        }
        assertNoPendingRenew(order.getId());
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
        assertNoPendingRenew(order.getId());
        AssetDO asset = assetManager.getById(order.getAssetId());
        borrowManager.casUpdateStatus(order, BorrowOrderStatus.RETURNED.name());
        BorrowOrderDO latest = borrowOrderDao.selectById(order.getId());
        latest.setReturnedAt(LocalDateTime.now());
        latest.setReturnComment(blankToNull(comment));
        borrowOrderDao.updateById(latest);
        assetManager.confirmReturn(asset);
        borrowManager.saveLog(order.getId(), BorrowLogAction.CONFIRM_RETURN, admin.getUserId(), blankToNull(comment));
        notifyApplicant(order, asset, MessageType.RETURN_CONFIRM, "归还已确认",
                "单号：" + order.getOrderNo() + "\n设备：" + assetText(asset) + "\n设备已回到在库。");
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
        vo.setRenewPending(listPendingRenews());
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
        int maxDays = sysParamService.borrowMaxDays();
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
        BorrowRenewDO pendingRenew = findPendingRenew(order.getId());
        if (pendingRenew != null) {
            vo.setPendingRenewId(pendingRenew.getId());
            vo.setPendingRenewDate(pendingRenew.getNewReturnDate());
            vo.setPendingRenewReason(pendingRenew.getReason());
        }
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

    private void notifyPending(BorrowOrderDO order, AssetDO asset, AuthUser applicant) {
        SysUserDO user = sysUserDao.selectById(applicant.getUserId());
        String name = user == null ? "" : user.getRealName();
        String content = "单号：" + order.getOrderNo()
                + "\n申请人：" + name
                + "\n设备：" + assetText(asset)
                + "\n预计归还日：" + order.getExpectedReturnDate();
        messageManager.sendToAdmins(MessageType.PENDING_APPROVAL, "有新的领用申请待审批", content, order.getId());
    }

    private void notifyApplicant(BorrowOrderDO order, AssetDO asset, MessageType type, String title, String content) {
        messageManager.send(order.getApplicantId(), type, title, content, order.getId());
    }

    private String assetText(AssetDO asset) {
        return asset.getAssetNo() + " " + asset.getName();
    }

    @Override
    @Transactional
    public BorrowRenewVO applyRenew(AuthUser applicant, Long orderId, RenewApplyDTO dto) {
        BorrowOrderDO order = requireOwn(applicant, orderId);
        if (!BorrowOrderStatus.BORROWING.name().equals(order.getStatus())) {
            throw new BusinessException("该单据当前为" + orderStatusLabel(order.getStatus()) + "，不能续借");
        }
        if (order.getExpectedReturnDate() == null || order.getIssuedAt() == null) {
            throw new ConflictException();
        }
        assertNoPendingRenew(order.getId());
        if (StrUtil.isBlank(dto.getReason())) {
            throw new BusinessException("续借理由不能为空");
        }
        if (dto.getNewReturnDate() == null || !dto.getNewReturnDate().isAfter(order.getExpectedReturnDate())) {
            throw new BusinessException("新预计归还日必须晚于原预计归还日");
        }
        LocalDate issuedDate = order.getIssuedAt().toLocalDate();
        int maxDays = sysParamService.renewMaxDaysFromIssue();
        if (dto.getNewReturnDate().isAfter(issuedDate.plusDays(maxDays))) {
            throw new BusinessException("自发放日起不能超过 " + maxDays + " 天");
        }
        BorrowRenewDO renew = new BorrowRenewDO();
        renew.setBorrowId(order.getId());
        renew.setOldReturnDate(order.getExpectedReturnDate());
        renew.setNewReturnDate(dto.getNewReturnDate());
        renew.setReason(dto.getReason().trim());
        renew.setStatus(RenewStatus.PENDING.name());
        renew.setVersion(0);
        borrowRenewDao.insert(renew);
        borrowManager.saveLog(order.getId(), BorrowLogAction.RENEW_APPLY, applicant.getUserId(), renew.getReason());
        AssetDO asset = assetManager.getById(order.getAssetId());
        String content = "单号：" + order.getOrderNo()
                + "\n申请人：" + applicantName(applicant.getUserId())
                + "\n设备：" + assetText(asset)
                + "\n原预计归还日：" + order.getExpectedReturnDate()
                + "\n新预计归还日：" + dto.getNewReturnDate()
                + "\n理由：" + renew.getReason();
        messageManager.sendToAdmins(MessageType.PENDING_APPROVAL, "有新的续借申请待审批", content, order.getId());
        return toRenewVo(borrowRenewDao.selectById(renew.getId()));
    }

    @Override
    @Transactional
    public BorrowRenewVO approveRenew(AuthUser admin, Long renewId) {
        if (!admin.isAdmin()) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "无权限");
        }
        BorrowRenewDO renew = requirePendingRenew(renewId);
        BorrowOrderDO order = borrowManager.getById(renew.getBorrowId());
        if (!BorrowOrderStatus.BORROWING.name().equals(order.getStatus())) {
            throw new ConflictException();
        }
        AssetDO asset = assetManager.getById(order.getAssetId());
        if (!AssetStatus.BORROWED.name().equals(asset.getStatus())) {
            throw new ConflictException();
        }
        int renewRows = borrowRenewDao.casFinish(
                renew.getId(),
                renew.getVersion(),
                RenewStatus.PENDING.name(),
                RenewStatus.APPROVED.name(),
                admin.getUserId(),
                LocalDateTime.now(),
                null
        );
        if (renewRows == 0) {
            throw new ConflictException();
        }
        int orderRows = borrowOrderDao.casUpdateExpectedReturn(
                order.getId(),
                order.getVersion(),
                BorrowOrderStatus.BORROWING.name(),
                renew.getNewReturnDate()
        );
        if (orderRows == 0) {
            throw new ConflictException();
        }
        assetManager.updateExpectedReturn(asset, renew.getNewReturnDate());
        borrowManager.saveLog(order.getId(), BorrowLogAction.RENEW_APPROVE, admin.getUserId(), null);
        String content = "单号：" + order.getOrderNo()
                + "\n设备：" + assetText(asset)
                + "\n预计归还日已更新为：" + renew.getNewReturnDate();
        notifyRenewResult(order, "续借已通过", content);
        return toRenewVo(borrowRenewDao.selectById(renew.getId()));
    }

    @Override
    @Transactional
    public BorrowRenewVO rejectRenew(AuthUser admin, Long renewId, String comment) {
        if (!admin.isAdmin()) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "无权限");
        }
        if (StrUtil.isBlank(comment)) {
            throw new BusinessException("驳回必须填写原因");
        }
        BorrowRenewDO renew = requirePendingRenew(renewId);
        BorrowOrderDO order = borrowManager.getById(renew.getBorrowId());
        LocalDate oldDate = order.getExpectedReturnDate();
        int rows = borrowRenewDao.casFinish(
                renew.getId(),
                renew.getVersion(),
                RenewStatus.PENDING.name(),
                RenewStatus.REJECTED.name(),
                admin.getUserId(),
                LocalDateTime.now(),
                comment.trim()
        );
        if (rows == 0) {
            throw new ConflictException();
        }
        borrowManager.saveLog(order.getId(), BorrowLogAction.RENEW_REJECT, admin.getUserId(), comment.trim());
        AssetDO asset = assetManager.getById(order.getAssetId());
        String content = "单号：" + order.getOrderNo()
                + "\n设备：" + assetText(asset)
                + "\n预计归还日仍为：" + oldDate
                + "\n原因：" + comment.trim();
        notifyRenewResult(order, "续借已驳回", content);
        return toRenewVo(borrowRenewDao.selectById(renew.getId()));
    }

    private void assertNoPendingRenew(Long borrowId) {
        if (findPendingRenew(borrowId) != null) {
            throw new BusinessException("该单据已有待审批的续借");
        }
    }

    private BorrowRenewDO findPendingRenew(Long borrowId) {
        return borrowRenewDao.selectOne(Wrappers.<BorrowRenewDO>lambdaQuery()
                .eq(BorrowRenewDO::getBorrowId, borrowId)
                .eq(BorrowRenewDO::getStatus, RenewStatus.PENDING.name())
                .last("LIMIT 1"));
    }

    private BorrowRenewDO requirePendingRenew(Long renewId) {
        BorrowRenewDO renew = borrowRenewDao.selectById(renewId);
        if (renew == null) {
            throw new NotFoundException("续借申请不存在");
        }
        if (!RenewStatus.PENDING.name().equals(renew.getStatus())) {
            throw new ConflictException();
        }
        return renew;
    }

    private List<BorrowRenewVO> listPendingRenews() {
        return borrowRenewDao.selectList(Wrappers.<BorrowRenewDO>lambdaQuery()
                        .eq(BorrowRenewDO::getStatus, RenewStatus.PENDING.name())
                        .orderByDesc(BorrowRenewDO::getId))
                .stream()
                .map(this::toRenewVo)
                .toList();
    }

    private BorrowRenewVO toRenewVo(BorrowRenewDO renew) {
        BorrowRenewVO vo = new BorrowRenewVO();
        vo.setId(renew.getId());
        vo.setBorrowId(renew.getBorrowId());
        vo.setOldReturnDate(renew.getOldReturnDate());
        vo.setNewReturnDate(renew.getNewReturnDate());
        vo.setReason(renew.getReason());
        vo.setStatus(renew.getStatus());
        vo.setStatusLabel(RenewStatus.valueOf(renew.getStatus()).getLabel());
        vo.setApproveComment(renew.getApproveComment());
        vo.setCreatedAt(renew.getCreatedAt());
        BorrowOrderDO order = borrowOrderDao.selectById(renew.getBorrowId());
        if (order != null) {
            vo.setOrderNo(order.getOrderNo());
            SysUserDO applicant = sysUserDao.selectById(order.getApplicantId());
            vo.setApplicantName(applicant == null ? null : applicant.getRealName());
            AssetDO asset = assetDao.selectById(order.getAssetId());
            if (asset != null) {
                vo.setAssetNo(asset.getAssetNo());
                vo.setAssetName(asset.getName());
            }
        }
        return vo;
    }

    private void notifyRenewResult(BorrowOrderDO order, String title, String content) {
        messageManager.send(order.getApplicantId(), MessageType.RENEW_RESULT, title, content, order.getId());
        messageManager.sendToAdmins(MessageType.RENEW_RESULT, title, content, order.getId());
    }

    private String applicantName(Long userId) {
        SysUserDO user = sysUserDao.selectById(userId);
        return user == null ? "" : user.getRealName();
    }
}
