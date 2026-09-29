package com.ikrai.project.service.audit;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.event.AnalysisEventListener;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.ikrai.project.common.AuthUser;
import com.ikrai.project.common.enums.UserRole;
import com.ikrai.project.common.exception.BusinessException;
import com.ikrai.project.dao.AuditLogDao;
import com.ikrai.project.dao.SysUserDao;
import com.ikrai.project.dataobject.AuditLogDO;
import com.ikrai.project.dataobject.SysUserDO;
import com.ikrai.project.excel.AuditExportRow;
import com.ikrai.project.query.AuditQuery;
import com.ikrai.project.vo.AuditLogVO;
import com.ikrai.project.vo.PageVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AuditServiceTest {

    private static final AtomicInteger SEQ = new AtomicInteger(1);

    @Autowired
    private AuditService auditService;
    @Autowired
    private AuditLogDao auditLogDao;
    @Autowired
    private SysUserDao sysUserDao;
    @Autowired
    private PasswordEncoder passwordEncoder;

    private AuthUser admin;
    private AuthUser user;
    private String token;

    @BeforeEach
    void setUp() {
        token = "AUD-" + SEQ.getAndIncrement();
        SysUserDO adminRow = user("audit_admin_" + token, "审计管理员", UserRole.ADMIN);
        SysUserDO userRow = user("audit_user_" + token, "审计普通用户", UserRole.USER);
        admin = new AuthUser(adminRow.getId(), adminRow.getUsername(), adminRow.getRole(), adminRow.getTokenVersion());
        user = new AuthUser(userRow.getId(), userRow.getUsername(), userRow.getRole(), userRow.getTokenVersion());
        insert(adminRow.getId(), "ASSET", "TRANSFER", token + "-A", LocalDate.now().minusDays(1).atTime(23, 59));
        insert(userRow.getId(), "DEPT", "CREATE", token + "-B", LocalDate.now().atStartOfDay());
    }

    @Test
    void userCannotQuery() {
        BusinessException ex = assertThrows(BusinessException.class, () -> auditService.list(new AuditQuery(), user));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
        assertEquals("无权限", ex.getMessage());
    }

    @Test
    void filtersByDateOperatorModuleAndObjectNo() {
        AuditQuery today = query();
        today.setFrom(LocalDate.now());
        today.setTo(LocalDate.now());
        PageVO<AuditLogVO> todayPage = auditService.list(today, admin);
        assertEquals(1, todayPage.getTotal());
        assertEquals(token + "-B", todayPage.getList().get(0).getObjectNo());
        assertEquals("部门", todayPage.getList().get(0).getModuleLabel());
        assertEquals("新增", todayPage.getList().get(0).getActionLabel());
        assertEquals("审计普通用户", todayPage.getList().get(0).getOperatorName());

        AuditQuery byModule = query();
        byModule.setModule("ASSET");
        assertEquals(token + "-A", auditService.list(byModule, admin).getList().get(0).getObjectNo());

        AuditQuery byObject = query();
        byObject.setObjectNo(token + "-A");
        assertEquals(1, auditService.list(byObject, admin).getTotal());

        AuditQuery byName = query();
        byName.setOperator("审计管理员");
        PageVO<AuditLogVO> named = auditService.list(byName, admin);
        assertEquals(1, named.getTotal());
        assertEquals(token + "-A", named.getList().get(0).getObjectNo());

        AuditQuery unknown = query();
        unknown.setOperator("不存在的人" + token);
        assertEquals(0, auditService.list(unknown, admin).getTotal());
    }

    @Test
    void fromAfterToFails() {
        AuditQuery query = query();
        query.setFrom(LocalDate.now());
        query.setTo(LocalDate.now().minusDays(1));
        BusinessException ex = assertThrows(BusinessException.class, () -> auditService.list(query, admin));
        assertEquals("开始日不得晚于结束日", ex.getMessage());
    }

    @Test
    void emptyExportFailsAndDoesNotWriteAudit() {
        long before = auditLogDao.selectCount(Wrappers.<AuditLogDO>lambdaQuery().eq(AuditLogDO::getModule, "AUDIT"));
        AuditQuery query = query();
        query.setObjectNo("NONE-" + token);
        BusinessException ex = assertThrows(BusinessException.class, () -> auditService.export(query, admin));
        assertEquals("没有可导出的审计日志", ex.getMessage());
        assertEquals(before, auditLogDao.selectCount(Wrappers.<AuditLogDO>lambdaQuery().eq(AuditLogDO::getModule, "AUDIT")));
    }

    @Test
    void exportMatchesFilterAndWritesAuditAfterFile() {
        AuditQuery query = query();
        query.setObjectNo(token + "-A");
        byte[] bytes = auditService.export(query, admin);
        assertTrue(bytes.length > 4);
        assertEquals('P', bytes[0]);
        assertEquals('K', bytes[1]);

        List<AuditExportRow> rows = new ArrayList<>();
        EasyExcel.read(new ByteArrayInputStream(bytes), AuditExportRow.class, new AnalysisEventListener<AuditExportRow>() {
            @Override
            public void invoke(AuditExportRow data, AnalysisContext context) {
                rows.add(data);
            }

            @Override
            public void doAfterAllAnalysed(AnalysisContext context) {
            }
        }).sheet().doRead();
        assertEquals(1, rows.size());
        assertEquals("设备", rows.get(0).getModuleLabel());
        assertEquals("调拨", rows.get(0).getActionLabel());
        assertEquals(token + "-A", rows.get(0).getObjectNo());
        assertEquals("审计管理员", rows.get(0).getOperatorName());

        AuditLogDO exported = auditLogDao.selectOne(Wrappers.<AuditLogDO>lambdaQuery()
                .eq(AuditLogDO::getModule, "AUDIT")
                .eq(AuditLogDO::getAction, "EXPORT")
                .eq(AuditLogDO::getOperatorId, admin.getUserId()));
        assertEquals("导出 1 条审计日志", exported.getSummary());
    }

    private AuditQuery query() {
        AuditQuery query = new AuditQuery();
        query.setObjectNo(token);
        return query;
    }

    private void insert(Long operatorId, String module, String action, String objectNo, LocalDateTime createdAt) {
        AuditLogDO log = new AuditLogDO();
        log.setOperatorId(operatorId);
        log.setModule(module);
        log.setAction(action);
        log.setObjectNo(objectNo);
        log.setSummary("测试 " + objectNo);
        log.setCreatedAt(createdAt);
        auditLogDao.insert(log);
    }

    private SysUserDO user(String username, String realName, UserRole role) {
        SysUserDO row = new SysUserDO();
        row.setUsername(username);
        row.setPasswordHash(passwordEncoder.encode("Passw0rd"));
        row.setRealName(realName);
        row.setEmail(username + "@example.com");
        row.setRole(role.name());
        row.setEnabled(1);
        row.setTokenVersion(0);
        row.setMustChangePassword(0);
        sysUserDao.insert(row);
        return row;
    }
}
