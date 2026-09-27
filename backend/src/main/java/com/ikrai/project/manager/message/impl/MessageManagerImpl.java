package com.ikrai.project.manager.message.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.ikrai.project.common.enums.MessageType;
import com.ikrai.project.common.enums.UserRole;
import com.ikrai.project.dao.SiteMessageDao;
import com.ikrai.project.dao.SysUserDao;
import com.ikrai.project.dataobject.SiteMessageDO;
import com.ikrai.project.dataobject.SysUserDO;
import com.ikrai.project.manager.message.MessageManager;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class MessageManagerImpl implements MessageManager {

    private final SiteMessageDao siteMessageDao;
    private final SysUserDao sysUserDao;

    public MessageManagerImpl(SiteMessageDao siteMessageDao, SysUserDao sysUserDao) {
        this.siteMessageDao = siteMessageDao;
        this.sysUserDao = sysUserDao;
    }

    @Override
    public void send(Long receiverId, MessageType type, String title, String content, Long borrowId) {
        SiteMessageDO message = new SiteMessageDO();
        message.setReceiverId(receiverId);
        message.setTitle(title);
        message.setContent(content);
        message.setMsgType(type.name());
        message.setBorrowId(borrowId);
        message.setReadFlag(0);
        siteMessageDao.insert(message);
    }

    @Override
    public void sendToAdmins(MessageType type, String title, String content, Long borrowId) {
        List<SysUserDO> admins = sysUserDao.selectList(Wrappers.<SysUserDO>lambdaQuery()
                .eq(SysUserDO::getRole, UserRole.ADMIN.name())
                .eq(SysUserDO::getEnabled, 1));
        for (SysUserDO admin : admins) {
            send(admin.getId(), type, title, content, borrowId);
        }
    }
}
