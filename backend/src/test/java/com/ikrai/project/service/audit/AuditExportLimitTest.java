package com.ikrai.project.service.audit;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.ikrai.project.common.AuthUser;
import com.ikrai.project.common.enums.UserRole;
import com.ikrai.project.common.exception.BusinessException;
import com.ikrai.project.dao.AuditLogDao;
import com.ikrai.project.dao.SysUserDao;
import com.ikrai.project.dataobject.AuditLogDO;
import com.ikrai.project.dataobject.SysUserDO;
import com.ikrai.project.query.AuditQuery;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = "ams.excel.export-max-rows=1")
@Transactional
class AuditExportLimitTest {

    private static final AtomicInteger SEQ = new AtomicInteger(1);

    @Autowired
    private AuditService auditService;
    @Autowired
    private AuditLogDao auditLogDao;
    @Autowired
    private SysUserDao sysUserDao;
    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void exportOverLimitReturns400AndDoesNotWriteAudit() {
        String token = "LIM-" + SEQ.getAndIncrement();
        SysUserDO adminRow = new SysUserDO();
        adminRow.setUsername("audit_limit_" + token);
        adminRow.setPasswordHash(passwordEncoder.encode("Passw0rd"));
        adminRow.setRealName("上限管理员");
        adminRow.setEmail(token + "@example.com");
        adminRow.setRole(UserRole.ADMIN.name());
        adminRow.setEnabled(1);
        adminRow.setTokenVersion(0);
        adminRow.setMustChangePassword(0);
        sysUserDao.insert(adminRow);
        AuthUser admin = new AuthUser(adminRow.getId(), adminRow.getUsername(), adminRow.getRole(), adminRow.getTokenVersion());
        insert(adminRow.getId(), token + "-1");
        insert(adminRow.getId(), token + "-2");

        AuditQuery query = new AuditQuery();
        query.setObjectNo(token);
        BusinessException ex = assertThrows(BusinessException.class, () -> auditService.export(query, admin));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertTrue(ex.getMessage().contains("缩小筛选"));
        assertEquals(0, auditLogDao.selectCount(Wrappers.<AuditLogDO>lambdaQuery()
                .eq(AuditLogDO::getModule, "AUDIT")
                .eq(AuditLogDO::getOperatorId, admin.getUserId())));
    }

    private void insert(Long operatorId, String objectNo) {
        AuditLogDO log = new AuditLogDO();
        log.setOperatorId(operatorId);
        log.setModule("ASSET");
        log.setAction("UPDATE");
        log.setObjectNo(objectNo);
        log.setSummary(objectNo);
        log.setCreatedAt(LocalDateTime.now());
        auditLogDao.insert(log);
    }
}
