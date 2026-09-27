package com.ikrai.project.service.message.impl;

import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ikrai.project.common.AuthUser;
import com.ikrai.project.common.enums.MessageType;
import com.ikrai.project.common.exception.BusinessException;
import com.ikrai.project.common.exception.NotFoundException;
import com.ikrai.project.dao.SiteMessageDao;
import com.ikrai.project.dataobject.SiteMessageDO;
import com.ikrai.project.dto.MessageReadDTO;
import com.ikrai.project.query.MessageQuery;
import com.ikrai.project.service.message.MessageService;
import com.ikrai.project.vo.PageVO;
import com.ikrai.project.vo.SiteMessageVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class MessageServiceImpl implements MessageService {

    private final SiteMessageDao siteMessageDao;

    public MessageServiceImpl(SiteMessageDao siteMessageDao) {
        this.siteMessageDao = siteMessageDao;
    }

    @Override
    public PageVO<SiteMessageVO> list(AuthUser viewer, MessageQuery query) {
        int page = query.getPage() == null || query.getPage() < 1 ? 1 : query.getPage();
        int pageSize = query.getPageSize() == null ? 20 : Math.min(query.getPageSize(), 100);
        var wrapper = Wrappers.<SiteMessageDO>lambdaQuery()
                .eq(SiteMessageDO::getReceiverId, viewer.getUserId());
        if ("unread".equals(query.getBox())) {
            wrapper.eq(SiteMessageDO::getReadFlag, 0);
        } else if ("read".equals(query.getBox())) {
            wrapper.eq(SiteMessageDO::getReadFlag, 1);
        }
        wrapper.orderByDesc(SiteMessageDO::getCreatedAt).orderByDesc(SiteMessageDO::getId);
        Page<SiteMessageDO> result = siteMessageDao.selectPage(new Page<>(page, pageSize), wrapper);
        List<SiteMessageVO> list = result.getRecords().stream().map(this::toVo).toList();
        return new PageVO<>(list, result.getTotal(), page, pageSize);
    }

    @Override
    @Transactional
    public void markRead(AuthUser viewer, MessageReadDTO dto) {
        if (dto != null && Boolean.TRUE.equals(dto.getAll())) {
            siteMessageDao.update(null, Wrappers.<SiteMessageDO>lambdaUpdate()
                    .set(SiteMessageDO::getReadFlag, 1)
                    .eq(SiteMessageDO::getReceiverId, viewer.getUserId())
                    .eq(SiteMessageDO::getReadFlag, 0));
            return;
        }
        List<Long> ids = dto == null || dto.getIds() == null ? List.of() : dto.getIds().stream().distinct().toList();
        if (CollUtil.isEmpty(ids)) {
            throw new BusinessException("请选择要标记的消息");
        }
        Long owned = siteMessageDao.selectCount(Wrappers.<SiteMessageDO>lambdaQuery()
                .in(SiteMessageDO::getId, ids)
                .eq(SiteMessageDO::getReceiverId, viewer.getUserId()));
        if (owned == null || owned != ids.size()) {
            throw new NotFoundException("消息不存在");
        }
        siteMessageDao.update(null, Wrappers.<SiteMessageDO>lambdaUpdate()
                .set(SiteMessageDO::getReadFlag, 1)
                .in(SiteMessageDO::getId, ids)
                .eq(SiteMessageDO::getReceiverId, viewer.getUserId()));
    }

    @Override
    public long unreadCount(AuthUser viewer) {
        Long count = siteMessageDao.selectCount(Wrappers.<SiteMessageDO>lambdaQuery()
                .eq(SiteMessageDO::getReceiverId, viewer.getUserId())
                .eq(SiteMessageDO::getReadFlag, 0));
        return count == null ? 0 : count;
    }

    private SiteMessageVO toVo(SiteMessageDO message) {
        SiteMessageVO vo = new SiteMessageVO();
        vo.setId(message.getId());
        vo.setTitle(message.getTitle());
        vo.setContent(message.getContent());
        vo.setMsgType(message.getMsgType());
        vo.setMsgTypeLabel(label(message.getMsgType()));
        vo.setBorrowId(message.getBorrowId());
        vo.setRead(message.getReadFlag() != null && message.getReadFlag() == 1);
        vo.setCreatedAt(message.getCreatedAt());
        return vo;
    }

    private String label(String type) {
        try {
            return MessageType.valueOf(type).getLabel();
        } catch (Exception ex) {
            return type;
        }
    }
}
