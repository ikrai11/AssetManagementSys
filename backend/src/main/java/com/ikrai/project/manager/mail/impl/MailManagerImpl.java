package com.ikrai.project.manager.mail.impl;

import com.ikrai.project.common.enums.MailStatus;
import com.ikrai.project.common.exception.BusinessException;
import com.ikrai.project.common.exception.NotFoundException;
import com.ikrai.project.dao.MailRecordDao;
import com.ikrai.project.dataobject.MailRecordDO;
import com.ikrai.project.manager.mail.MailManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
public class MailManagerImpl implements MailManager {

    private final MailRecordDao mailRecordDao;
    private final MailDispatcher mailDispatcher;

    public MailManagerImpl(MailRecordDao mailRecordDao, MailDispatcher mailDispatcher) {
        this.mailRecordDao = mailRecordDao;
        this.mailDispatcher = mailDispatcher;
    }

    @Override
    public MailRecordDO create(Long receiverId, String email, String subject, String content, Long borrowId) {
        MailRecordDO record = new MailRecordDO();
        record.setReceiverId(receiverId);
        record.setSubject(subject);
        record.setContent(content);
        record.setBorrowId(borrowId);
        record.setRetryCount(0);
        mailDispatcher.decide(record, email);
        mailRecordDao.insert(record);
        scheduleIfPending(record);
        return record;
    }

    @Override
    public MailRecordDO retry(Long id) {
        MailRecordDO record = mailRecordDao.selectById(id);
        if (record == null) {
            throw new NotFoundException("邮件记录不存在");
        }
        if (MailStatus.SENT.name().equals(record.getStatus())) {
            throw new BusinessException("该邮件已发送，不能重试");
        }
        record.setRetryCount(record.getRetryCount() == null ? 1 : record.getRetryCount() + 1);
        mailDispatcher.decide(record, record.getEmail());
        if (MailStatus.PENDING.name().equals(record.getStatus())) {
            mailDispatcher.deliver(record);
        }
        mailRecordDao.updateById(record);
        return mailRecordDao.selectById(id);
    }

    private void scheduleIfPending(MailRecordDO record) {
        if (!MailStatus.PENDING.name().equals(record.getStatus()) || record.getId() == null) {
            return;
        }
        Long id = record.getId();
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    mailDispatcher.sendAsync(id);
                }
            });
            return;
        }
        mailDispatcher.sendAsync(id);
    }
}
