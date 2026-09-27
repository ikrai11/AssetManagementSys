package com.ikrai.project.service.mail;

import com.ikrai.project.common.AuthUser;
import com.ikrai.project.query.MailRecordQuery;
import com.ikrai.project.vo.MailRecordVO;
import com.ikrai.project.vo.PageVO;

public interface MailRecordService {

    PageVO<MailRecordVO> list(AuthUser viewer, MailRecordQuery query);

    MailRecordVO retry(AuthUser viewer, Long id);
}
