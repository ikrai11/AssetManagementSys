package com.ikrai.project.service.mail.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ikrai.project.common.AuthUser;
import com.ikrai.project.common.enums.MailStatus;
import com.ikrai.project.common.exception.BusinessException;
import com.ikrai.project.dao.MailRecordDao;
import com.ikrai.project.dao.SysUserDao;
import com.ikrai.project.dataobject.MailRecordDO;
import com.ikrai.project.dataobject.SysUserDO;
import com.ikrai.project.manager.mail.MailManager;
import com.ikrai.project.query.MailRecordQuery;
import com.ikrai.project.service.mail.MailRecordService;
import com.ikrai.project.vo.MailRecordVO;
import com.ikrai.project.vo.PageVO;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class MailRecordServiceImpl implements MailRecordService {

    private final MailRecordDao mailRecordDao;
    private final SysUserDao sysUserDao;
    private final MailManager mailManager;

    public MailRecordServiceImpl(MailRecordDao mailRecordDao, SysUserDao sysUserDao, MailManager mailManager) {
        this.mailRecordDao = mailRecordDao;
        this.sysUserDao = sysUserDao;
        this.mailManager = mailManager;
    }

    @Override
    public PageVO<MailRecordVO> list(AuthUser viewer, MailRecordQuery query) {
        requireAdmin(viewer);
        int page = query.getPage() == null || query.getPage() < 1 ? 1 : query.getPage();
        int pageSize = query.getPageSize() == null ? 20 : Math.min(query.getPageSize(), 100);
        var wrapper = Wrappers.<MailRecordDO>lambdaQuery();
        if (StrUtil.isNotBlank(query.getStatus())) {
            wrapper.eq(MailRecordDO::getStatus, query.getStatus());
        }
        wrapper.orderByDesc(MailRecordDO::getCreatedAt).orderByDesc(MailRecordDO::getId);
        Page<MailRecordDO> result = mailRecordDao.selectPage(new Page<>(page, pageSize), wrapper);
        List<MailRecordVO> list = result.getRecords().stream().map(this::toVo).toList();
        return new PageVO<>(list, result.getTotal(), page, pageSize);
    }

    @Override
    public MailRecordVO retry(AuthUser viewer, Long id) {
        requireAdmin(viewer);
        return toVo(mailManager.retry(id));
    }

    private void requireAdmin(AuthUser viewer) {
        if (viewer == null || !viewer.isAdmin()) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "无权限");
        }
    }

    private MailRecordVO toVo(MailRecordDO record) {
        MailRecordVO vo = new MailRecordVO();
        vo.setId(record.getId());
        vo.setReceiverId(record.getReceiverId());
        SysUserDO user = sysUserDao.selectById(record.getReceiverId());
        vo.setReceiverName(user == null ? null : user.getRealName());
        vo.setEmail(record.getEmail());
        vo.setSubject(record.getSubject());
        vo.setContent(record.getContent());
        vo.setBorrowId(record.getBorrowId());
        vo.setStatus(record.getStatus());
        vo.setStatusLabel(label(record.getStatus()));
        vo.setFailReason(record.getFailReason());
        vo.setRetryCount(record.getRetryCount());
        vo.setSentAt(record.getSentAt());
        vo.setCreatedAt(record.getCreatedAt());
        return vo;
    }

    private String label(String status) {
        try {
            return MailStatus.valueOf(status).getLabel();
        } catch (Exception ex) {
            return status;
        }
    }
}
