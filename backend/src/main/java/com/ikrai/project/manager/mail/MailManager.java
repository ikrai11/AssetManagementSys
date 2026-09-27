package com.ikrai.project.manager.mail;

import com.ikrai.project.dataobject.MailRecordDO;

public interface MailManager {

    MailRecordDO create(Long receiverId, String email, String subject, String content, Long borrowId);

    MailRecordDO retry(Long id);
}
