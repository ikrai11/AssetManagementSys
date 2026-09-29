package com.ikrai.project.service.mail;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.icegreen.greenmail.util.GreenMail;
import com.icegreen.greenmail.util.ServerSetup;
import com.ikrai.project.common.ParamKeys;
import com.ikrai.project.common.enums.MailStatus;
import com.ikrai.project.common.enums.UserRole;
import com.ikrai.project.common.exception.BusinessException;
import com.ikrai.project.dao.MailRecordDao;
import com.ikrai.project.dao.SysParamDao;
import com.ikrai.project.dao.SysUserDao;
import com.ikrai.project.dataobject.MailRecordDO;
import com.ikrai.project.dataobject.SysParamDO;
import com.ikrai.project.dataobject.SysUserDO;
import com.ikrai.project.manager.mail.MailManager;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

@SpringBootTest
@ActiveProfiles("test")
class SmtpMailTest {

    private static final int PORT = 18025;
    private static final GreenMail GREEN_MAIL = new GreenMail(new ServerSetup(PORT, "127.0.0.1", ServerSetup.PROTOCOL_SMTP));

    static {
        GREEN_MAIL.start();
    }

    @DynamicPropertySource
    static void smtp(DynamicPropertyRegistry registry) {
        registry.add("spring.mail.host", () -> "127.0.0.1");
        registry.add("spring.mail.port", () -> PORT);
        registry.add("ams.mail.enabled", () -> "true");
        registry.add("ams.mail.from", () -> "ams-test@localhost");
    }

    @AfterAll
    static void stopSmtp() {
        GREEN_MAIL.stop();
    }

    @Autowired
    private MailManager mailManager;
    @Autowired
    private MailRecordDao mailRecordDao;
    @Autowired
    private SysUserDao sysUserDao;
    @Autowired
    private SysParamDao sysParamDao;
    @Autowired
    private PasswordEncoder passwordEncoder;

    private SysUserDO holder;

    @BeforeEach
    void setUp() throws Exception {
        if (!GREEN_MAIL.isRunning()) {
            GREEN_MAIL.start();
        }
        GREEN_MAIL.purgeEmailFromAllMailboxes();
        holder = insertUser();
        setMailChannel(true);
    }

    @AfterEach
    void restoreChannel() {
        setMailChannel(true);
    }

    @Test
    void configuredSmtpSendsUtf8MailAndRejectsSecondSend() throws Exception {
        MailRecordDO created = mailManager.create(holder.getId(), "holder@localhost",
                "设备即将到期", "资产编号\t测试笔记本", null);

        MailRecordDO sent = awaitStatus(created.getId(), MailStatus.SENT.name());
        assertEquals("holder@localhost", sent.getEmail());
        assertTrue(sent.getSentAt() != null);

        MimeMessage[] messages = GREEN_MAIL.getReceivedMessages();
        assertEquals(1, messages.length);
        assertEquals("设备即将到期", messages[0].getSubject());
        assertEquals("ams-test@localhost", messages[0].getFrom()[0].toString());
        assertEquals("holder@localhost", messages[0].getAllRecipients()[0].toString());
        String body = new String(messages[0].getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertTrue(body.contains("测试笔记本"));

        BusinessException again = assertThrows(BusinessException.class, () -> mailManager.retry(sent.getId()));
        assertTrue(again.getMessage().contains("已发送"));
        assertEquals(1, GREEN_MAIL.getReceivedMessages().length);
    }

    @Test
    void blankEmailAndClosedChannelDoNotSend() {
        MailRecordDO blank = mailManager.create(holder.getId(), "  ", "空邮箱", "正文", null);
        assertEquals(MailStatus.SKIPPED.name(), blank.getStatus());
        assertEquals("收件人邮箱为空", blank.getFailReason());

        setMailChannel(false);
        MailRecordDO closed = mailManager.create(holder.getId(), "holder@localhost", "通道关闭", "正文", null);
        assertEquals(MailStatus.SKIPPED.name(), closed.getStatus());
        assertEquals("邮件通道已关闭", closed.getFailReason());
        assertEquals(0, GREEN_MAIL.getReceivedMessages().length);
    }

    @Test
    void unreachableSmtpFailsFastThenRetrySendsOnce() {
        GREEN_MAIL.stop();
        long started = System.nanoTime();
        MailRecordDO created = mailManager.create(holder.getId(), "holder@localhost", "发送失败后再试", "请重试", null);
        MailRecordDO failed = awaitStatus(created.getId(), MailStatus.FAILED.name());
        long elapsedMs = (System.nanoTime() - started) / 1_000_000;
        assertTrue(elapsedMs < 8000, "连接无响应时没有在超时内结束，实际 " + elapsedMs + "ms");
        assertTrue(failed.getFailReason() != null && !failed.getFailReason().isBlank());
        assertEquals(0, failed.getRetryCount());

        GREEN_MAIL.start();
        MailRecordDO retried = mailRecordDao.selectById(mailManager.retry(failed.getId()).getId());
        assertEquals(MailStatus.SENT.name(), retried.getStatus());
        assertEquals(1, retried.getRetryCount());
        assertEquals(1, GREEN_MAIL.getReceivedMessages().length);
    }

    private MailRecordDO awaitStatus(Long id, String status) {
        long deadline = System.nanoTime() + 8_000_000_000L;
        MailRecordDO latest = null;
        while (System.nanoTime() < deadline) {
            latest = mailRecordDao.selectById(id);
            if (latest != null && status.equals(latest.getStatus())) {
                return latest;
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        fail("邮件没有变为 " + status + "，当前是 " + (latest == null ? "空" : latest.getStatus() + " " + latest.getFailReason()));
        return latest;
    }

    private void setMailChannel(boolean enabled) {
        SysParamDO param = sysParamDao.selectOne(Wrappers.<SysParamDO>lambdaQuery()
                .eq(SysParamDO::getParamKey, ParamKeys.MAIL_CHANNEL));
        param.setParamValue(Boolean.toString(enabled));
        sysParamDao.updateById(param);
    }

    private SysUserDO insertUser() {
        SysUserDO row = new SysUserDO();
        row.setUsername("smtp_" + System.nanoTime());
        row.setPasswordHash(passwordEncoder.encode("Passw0rd!"));
        row.setRealName("邮件测试");
        row.setEmail("holder@localhost");
        row.setRole(UserRole.USER.name());
        row.setEnabled(1);
        row.setTokenVersion(0);
        row.setMustChangePassword(0);
        sysUserDao.insert(row);
        return row;
    }
}
