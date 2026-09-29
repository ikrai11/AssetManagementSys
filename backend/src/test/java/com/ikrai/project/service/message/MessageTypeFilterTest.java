package com.ikrai.project.service.message;

import com.ikrai.project.common.AuthUser;
import com.ikrai.project.common.enums.MessageType;
import com.ikrai.project.common.enums.UserRole;
import com.ikrai.project.common.exception.BusinessException;
import com.ikrai.project.dao.SiteMessageDao;
import com.ikrai.project.dao.SysUserDao;
import com.ikrai.project.dataobject.SiteMessageDO;
import com.ikrai.project.dataobject.SysUserDO;
import com.ikrai.project.query.MessageQuery;
import com.ikrai.project.vo.PageVO;
import com.ikrai.project.vo.SiteMessageVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class MessageTypeFilterTest {

    private static final AtomicInteger SEQ = new AtomicInteger(1);

    @Autowired
    private MessageService messageService;
    @Autowired
    private SiteMessageDao siteMessageDao;
    @Autowired
    private SysUserDao sysUserDao;
    @Autowired
    private PasswordEncoder passwordEncoder;

    private AuthUser owner;
    private AuthUser other;

    @BeforeEach
    void setUp() {
        owner = user("msg_owner_");
        other = user("msg_other_");
        insert(owner.getUserId(), MessageType.ISSUE, "已发放", 0);
        insert(owner.getUserId(), MessageType.DUE_REMIND, "即将到期", 0);
        insert(owner.getUserId(), MessageType.DUE_REMIND, "已读到期", 1);
        insert(other.getUserId(), MessageType.ISSUE, "别人的发放", 0);
    }

    @Test
    void filtersOwnMessagesByType() {
        MessageQuery query = new MessageQuery();
        query.setMsgType(MessageType.ISSUE.name());
        PageVO<SiteMessageVO> page = messageService.list(owner, query);
        assertEquals(1, page.getTotal());
        assertEquals("已发放", page.getList().get(0).getTitle());
        assertEquals("发放", page.getList().get(0).getMsgTypeLabel());
    }

    @Test
    void unreadBoxAndTypeApplyTogether() {
        MessageQuery query = new MessageQuery();
        query.setBox("unread");
        query.setMsgType(MessageType.DUE_REMIND.name());
        PageVO<SiteMessageVO> page = messageService.list(owner, query);
        assertEquals(1, page.getTotal());
        assertEquals("即将到期", page.getList().get(0).getTitle());
        assertTrue(page.getList().stream().noneMatch(SiteMessageVO::getRead));
    }

    @Test
    void unknownTypeFails() {
        MessageQuery query = new MessageQuery();
        query.setMsgType("SECRET");
        BusinessException ex = assertThrows(BusinessException.class, () -> messageService.list(owner, query));
        assertEquals("不支持的消息类型", ex.getMessage());
    }

    private AuthUser user(String prefix) {
        SysUserDO row = new SysUserDO();
        row.setUsername(prefix + SEQ.getAndIncrement());
        row.setPasswordHash(passwordEncoder.encode("Passw0rd"));
        row.setRealName(prefix);
        row.setEmail(row.getUsername() + "@example.com");
        row.setRole(UserRole.USER.name());
        row.setEnabled(1);
        row.setTokenVersion(0);
        row.setMustChangePassword(0);
        sysUserDao.insert(row);
        return new AuthUser(row.getId(), row.getUsername(), row.getRole(), row.getTokenVersion());
    }

    private void insert(Long receiverId, MessageType type, String title, int readFlag) {
        SiteMessageDO message = new SiteMessageDO();
        message.setReceiverId(receiverId);
        message.setTitle(title);
        message.setContent(title);
        message.setMsgType(type.name());
        message.setReadFlag(readFlag);
        siteMessageDao.insert(message);
    }
}
