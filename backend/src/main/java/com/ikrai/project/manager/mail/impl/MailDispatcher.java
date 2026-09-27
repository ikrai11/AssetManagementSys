package com.ikrai.project.manager.mail.impl;

import cn.hutool.core.util.StrUtil;
import com.ikrai.project.common.enums.MailStatus;
import com.ikrai.project.config.AmsProperties;
import com.ikrai.project.dao.MailRecordDao;
import com.ikrai.project.dataobject.MailRecordDO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class MailDispatcher {

    static final String CHANNEL_CLOSED = "邮件通道未配置";
    static final String EMPTY_EMAIL = "收件人邮箱为空";

    private static final Logger log = LoggerFactory.getLogger(MailDispatcher.class);

    private final MailRecordDao mailRecordDao;
    private final AmsProperties amsProperties;
    private final ObjectProvider<JavaMailSender> mailSender;

    public MailDispatcher(MailRecordDao mailRecordDao,
                          AmsProperties amsProperties,
                          ObjectProvider<JavaMailSender> mailSender) {
        this.mailRecordDao = mailRecordDao;
        this.amsProperties = amsProperties;
        this.mailSender = mailSender;
    }

    public boolean configured() {
        return amsProperties.getMail().isEnabled()
                && StrUtil.isNotBlank(amsProperties.getMail().getFrom())
                && mailSender.getIfAvailable() != null;
    }

    public void decide(MailRecordDO record, String email) {
        record.setSentAt(null);
        if (StrUtil.isBlank(email)) {
            record.setEmail(null);
            record.setStatus(MailStatus.SKIPPED.name());
            record.setFailReason(EMPTY_EMAIL);
            return;
        }
        record.setEmail(email.trim());
        if (!configured()) {
            record.setStatus(MailStatus.SKIPPED.name());
            record.setFailReason(CHANNEL_CLOSED);
            return;
        }
        record.setStatus(MailStatus.PENDING.name());
        record.setFailReason(null);
    }

    public void deliver(MailRecordDO record) {
        if (!configured() || StrUtil.isBlank(record.getEmail())) {
            decide(record, record.getEmail());
            return;
        }
        JavaMailSender sender = mailSender.getIfAvailable();
        if (sender == null) {
            record.setStatus(MailStatus.SKIPPED.name());
            record.setFailReason(CHANNEL_CLOSED);
            return;
        }
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(amsProperties.getMail().getFrom());
            message.setTo(record.getEmail());
            message.setSubject(record.getSubject());
            message.setText(record.getContent());
            sender.send(message);
            record.setStatus(MailStatus.SENT.name());
            record.setFailReason(null);
            record.setSentAt(LocalDateTime.now());
        } catch (Exception ex) {
            record.setStatus(MailStatus.FAILED.name());
            record.setFailReason(StrUtil.maxLength(StrUtil.blankToDefault(ex.getMessage(), "发送失败"), 500));
            log.info("邮件记录 {} 发送失败", record.getId());
        }
    }

    @Async
    public void sendAsync(Long id) {
        MailRecordDO record = mailRecordDao.selectById(id);
        if (record == null || !MailStatus.PENDING.name().equals(record.getStatus())) {
            return;
        }
        deliver(record);
        mailRecordDao.updateById(record);
        log.info("邮件记录 {} 状态 {}", id, record.getStatus());
    }
}
