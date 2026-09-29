package com.ikrai.project.service.user;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.ikrai.project.common.AuthUser;
import com.ikrai.project.common.enums.UserRole;
import com.ikrai.project.common.exception.BusinessException;
import com.ikrai.project.dao.AuditLogDao;
import com.ikrai.project.dao.SysUserDao;
import com.ikrai.project.dataobject.AuditLogDO;
import com.ikrai.project.dataobject.SysUserDO;
import com.ikrai.project.dto.ProfileUpdateDTO;
import com.ikrai.project.vo.UserVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ProfileServiceTest {

    private static final AtomicInteger SEQ = new AtomicInteger(1);

    @Autowired
    private AuthService authService;
    @Autowired
    private AuditLogDao auditLogDao;
    @Autowired
    private SysUserDao sysUserDao;
    @Autowired
    private PasswordEncoder passwordEncoder;

    private AuthUser user;
    private String username;

    @BeforeEach
    void setUp() {
        SysUserDO row = insertUser("profile_user", UserRole.USER, "old@example.com");
        username = row.getUsername();
        user = asUser(row);
    }

    @Test
    void userUpdatesNameAndEmailWithoutWritingAddressOrPassword() {
        ProfileUpdateDTO dto = new ProfileUpdateDTO();
        dto.setRealName("新姓名");
        dto.setEmail("new.mail@example.com");

        UserVO saved = authService.updateProfile(user, dto);

        SysUserDO row = sysUserDao.selectById(user.getUserId());
        assertEquals("新姓名", saved.getRealName());
        assertEquals("new.mail@example.com", row.getEmail());
        assertEquals(UserRole.USER.name(), row.getRole());
        assertTrue(passwordEncoder.matches("Passw0rd!", row.getPasswordHash()));
        List<AuditLogDO> logs = audits(username);
        assertEquals(1, logs.size());
        assertEquals("USER", logs.get(0).getModule());
        assertEquals("UPDATE", logs.get(0).getAction());
        assertTrue(logs.get(0).getSummary().contains("姓名由「profile_user」改为「新姓名」"));
        assertTrue(logs.get(0).getSummary().contains("邮箱已更新"));
        assertFalse(logs.get(0).getSummary().contains("new.mail@example.com"));
        assertFalse(logs.get(0).getSummary().contains("Passw0rd!"));
    }

    @Test
    void blankEmailForUserWritesNothing() {
        ProfileUpdateDTO dto = new ProfileUpdateDTO();
        dto.setRealName("profile_user");
        dto.setEmail("  ");

        BusinessException ex = assertThrows(BusinessException.class, () -> authService.updateProfile(user, dto));

        assertTrue(ex.getMessage().contains("邮箱"));
        assertEquals("old@example.com", sysUserDao.selectById(user.getUserId()).getEmail());
        assertEquals(0, audits(username).size());
    }

    @Test
    void invalidEmailWritesNothing() {
        ProfileUpdateDTO dto = new ProfileUpdateDTO();
        dto.setRealName("profile_user");
        dto.setEmail("not-an-email");

        BusinessException ex = assertThrows(BusinessException.class, () -> authService.updateProfile(user, dto));

        assertTrue(ex.getMessage().contains("邮箱"));
        assertEquals("old@example.com", sysUserDao.selectById(user.getUserId()).getEmail());
        assertEquals(0, audits(username).size());
    }

    @Test
    void unchangedProfileWritesNoAudit() {
        ProfileUpdateDTO dto = new ProfileUpdateDTO();
        dto.setRealName("profile_user");
        dto.setEmail("old@example.com");

        UserVO saved = authService.updateProfile(user, dto);

        assertEquals("old@example.com", saved.getEmail());
        assertEquals(0, audits(username).size());
    }

    @Test
    void adminCanClearEmail() {
        SysUserDO adminRow = insertUser("profile_admin", UserRole.ADMIN, "admin@example.com");
        AuthUser admin = asUser(adminRow);
        ProfileUpdateDTO dto = new ProfileUpdateDTO();
        dto.setRealName(adminRow.getRealName());
        dto.setEmail("");

        UserVO saved = authService.updateProfile(admin, dto);

        assertNull(saved.getEmail());
        assertNull(sysUserDao.selectById(admin.getUserId()).getEmail());
        List<AuditLogDO> logs = audits(adminRow.getUsername());
        assertEquals(1, logs.size());
        assertTrue(logs.get(0).getSummary().contains("邮箱已更新"));
        assertFalse(logs.get(0).getSummary().contains("admin@example.com"));
    }

    private List<AuditLogDO> audits(String objectNo) {
        return auditLogDao.selectList(Wrappers.<AuditLogDO>lambdaQuery()
                .eq(AuditLogDO::getModule, "USER")
                .eq(AuditLogDO::getObjectNo, objectNo));
    }

    private SysUserDO insertUser(String name, UserRole role, String email) {
        SysUserDO row = new SysUserDO();
        row.setUsername(name + SEQ.getAndIncrement());
        row.setPasswordHash(passwordEncoder.encode("Passw0rd!"));
        row.setRealName(name);
        row.setEmail(email);
        row.setRole(role.name());
        row.setEnabled(1);
        row.setTokenVersion(0);
        row.setMustChangePassword(0);
        sysUserDao.insert(row);
        return row;
    }

    private AuthUser asUser(SysUserDO row) {
        return new AuthUser(row.getId(), row.getUsername(), row.getRole(), row.getTokenVersion());
    }
}
