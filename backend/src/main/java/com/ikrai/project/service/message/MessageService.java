package com.ikrai.project.service.message;

import com.ikrai.project.common.AuthUser;
import com.ikrai.project.dto.MessageReadDTO;
import com.ikrai.project.query.MessageQuery;
import com.ikrai.project.vo.PageVO;
import com.ikrai.project.vo.SiteMessageVO;

public interface MessageService {

    PageVO<SiteMessageVO> list(AuthUser viewer, MessageQuery query);

    void markRead(AuthUser viewer, MessageReadDTO dto);

    long unreadCount(AuthUser viewer);
}
