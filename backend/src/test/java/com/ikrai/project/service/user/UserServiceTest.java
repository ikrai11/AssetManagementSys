package com.ikrai.project.service.user;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.ikrai.project.common.AuthUser;
import com.ikrai.project.common.enums.UserRole;
import com.ikrai.project.common.exception.BusinessException;
import com.ikrai.project.dao.AuditLogDao;
import com.ikrai.project.dao.SysDeptDao;
import com.ikrai.project.dao.SysUserDao;
import com.ikrai.project.dataobject.AuditLogDO;
import com.ikrai.project.dataobject.SysUserDO;
import com.ikrai.project.dto.LoginDTO;
import com.ikrai.project.dto.ResetPasswordDTO;
import com.ikrai.project.dto.UserSaveDTO;
import com.ikrai.project.query.UserQuery;
import com.ikrai.project.vo.PageVO;
import com.ikrai.project.vo.UserVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class UserServiceTest {

    private static final AtomicInteger SEQ = new AtomicInteger(1);

    @Autowired
    private UserService userService;
    @Autowired
    private AuditLogDao auditLogDao;
    @Autowired
    private AuthService authService;
    @Autowired
    private SysUserDao sysUserDao;
    @Autowired
    private SysDeptDao sysDeptDao;
    @Autowired
    private PasswordEncoder passwordEncoder;

    private AuthUser admin;

    @BeforeEach
    void setUp() {
        admin = asUser(insertUser("mgr", UserRole.ADMIN, 1));
    }

    @Test
    void createUserRequiresEmailForRegularUser() {
        UserSaveDTO dto = newUser("staff", UserRole.USER);
        dto.setEmail("  ");

        BusinessException ex = assertThrows(BusinessException.class, () -> userService.create(admin, dto));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertTrue(ex.getMessage().contains("邮箱"));
    }

    @Test
    void createUserRejectsInvalidEmail() {
        UserSaveDTO dto = newUser("badmail", UserRole.USER);
        dto.setEmail("not-an-email");

        BusinessException ex = assertThrows(BusinessException.class, () -> userService.create(admin, dto));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertTrue(ex.getMessage().contains("邮箱"));
    }

    @Test
    void createUserRejectsDuplicateUsername() {
        UserSaveDTO first = newUser("dup", UserRole.USER);
        userService.create(admin, first);
        UserSaveDTO second = newUser("dup", UserRole.USER);
        second.setUsername(first.getUsername());

        BusinessException ex = assertThrows(BusinessException.class, () -> userService.create(admin, second));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertTrue(ex.getMessage().contains("账号"));
    }

    @Test
    void disableSelfFails() {
        UserSaveDTO dto = new UserSaveDTO();
        dto.setRealName("自己");
        dto.setRole(UserRole.ADMIN.name());
        dto.setEnabled(false);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> userService.update(admin, admin.getUserId(), dto));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertTrue(ex.getMessage().contains("自己"));
        assertEquals(1, sysUserDao.selectById(admin.getUserId()).getEnabled());
    }

    @Test
    void disableLastAdminFails() {
        UserVO lastAdmin = userService.create(admin, newUser("onlyadmin", UserRole.ADMIN));
        disableAllSeedAdminsExcept(lastAdmin.getId());

        UserSaveDTO dto = new UserSaveDTO();
        dto.setRealName(lastAdmin.getRealName());
        dto.setRole(UserRole.ADMIN.name());
        dto.setEnabled(false);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> userService.update(admin, lastAdmin.getId(), dto));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertTrue(ex.getMessage().contains("管理员"));
        assertEquals(1, sysUserDao.selectById(lastAdmin.getId()).getEnabled());
    }

    @Test
    void disableUserBlocksLoginAndKeepsAccount() {
        UserVO created = userService.create(admin, newUser("gone", UserRole.USER));
        int version = sysUserDao.selectById(created.getId()).getTokenVersion();

        UserSaveDTO dto = new UserSaveDTO();
        dto.setRealName(created.getRealName());
        dto.setEmail(created.getEmail());
        dto.setRole(UserRole.USER.name());
        dto.setEnabled(false);
        userService.update(admin, created.getId(), dto);

        SysUserDO after = sysUserDao.selectById(created.getId());
        assertEquals(0, after.getEnabled());
        assertEquals(version + 1, after.getTokenVersion());

        LoginDTO login = new LoginDTO();
        login.setUsername(created.getUsername());
        login.setPassword("Passw0rd!");
        BusinessException ex = assertThrows(BusinessException.class, () -> authService.login(login));
        assertEquals("账号或密码错误", ex.getMessage());
    }

    @Test
    void resetPasswordForcesChangeOnNextLogin() {
        UserVO created = userService.create(admin, newUser("resetme", UserRole.USER));
        int version = sysUserDao.selectById(created.getId()).getTokenVersion();

        ResetPasswordDTO reset = new ResetPasswordDTO();
        reset.setPassword("Newpass12");
        userService.resetPassword(admin, created.getId(), reset);

        SysUserDO after = sysUserDao.selectById(created.getId());
        assertEquals(1, after.getMustChangePassword());
        assertEquals(version + 1, after.getTokenVersion());
        assertTrue(passwordEncoder.matches("Newpass12", after.getPasswordHash()));

        LoginDTO login = new LoginDTO();
        login.setUsername(created.getUsername());
        login.setPassword("Newpass12");
        assertTrue(authService.login(login).isMustChangePassword());
    }

    @Test
    void changeLastAdminRoleToUserFails() {
        UserVO created = userService.create(admin, newUser("keepadmin", UserRole.ADMIN));
        disableAllSeedAdminsExcept(created.getId());
        AuthUser operator = asUser(sysUserDao.selectById(created.getId()));

        UserSaveDTO dto = new UserSaveDTO();
        dto.setRealName(created.getRealName());
        dto.setRole(UserRole.USER.name());
        dto.setEmail("keepadmin@example.com");
        dto.setEnabled(true);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> userService.update(operator, created.getId(), dto));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertTrue(ex.getMessage().contains("管理员"));
        assertEquals(UserRole.ADMIN.name(), sysUserDao.selectById(created.getId()).getRole());
    }

    @Test
    void listFiltersByKeywordRoleDeptAndEnabled() {
        Long deptId = sysDeptDao.selectById(1L).getId();
        UserSaveDTO staff = newUser("filteru", UserRole.USER);
        staff.setRealName("筛选甲");
        staff.setDeptId(deptId);
        userService.create(admin, staff);

        UserSaveDTO other = newUser("otheradm", UserRole.ADMIN);
        other.setEnabled(false);
        userService.create(admin, other);

        UserQuery query = new UserQuery();
        query.setKeyword("筛选");
        query.setRole(UserRole.USER.name());
        query.setDeptId(deptId);
        query.setEnabled(true);
        PageVO<UserVO> page = userService.list(query);

        assertEquals(1, page.getTotal());
        assertEquals("筛选甲", page.getList().get(0).getRealName());
        assertEquals("普通用户", page.getList().get(0).getRoleLabel());
        assertFalse(page.getList().get(0).isMustChangePassword() && page.getList().get(0).getEmail() == null);
    }

    @Test
    void userChangesWriteAuditWithoutPassword() {
        UserSaveDTO dto = newUser("audited", UserRole.USER);
        UserVO created = userService.create(admin, dto);

        List<AuditLogDO> createdLogs = audits(created.getUsername());
        assertEquals(1, createdLogs.size());
        assertEquals("USER", createdLogs.get(0).getModule());
        assertEquals("CREATE", createdLogs.get(0).getAction());
        assertTrue(createdLogs.get(0).getSummary().contains("普通用户"));
        assertFalse(createdLogs.get(0).getSummary().contains(dto.getPassword()));

        UserSaveDTO roleChange = newUser("unused", UserRole.ADMIN);
        roleChange.setUsername(created.getUsername());
        roleChange.setRealName(created.getRealName());
        roleChange.setEmail(created.getEmail());
        userService.update(admin, created.getId(), roleChange);
        assertTrue(audits(created.getUsername()).stream()
                .anyMatch(item -> "UPDATE".equals(item.getAction()) && item.getSummary().contains("系统管理员")));

        ResetPasswordDTO reset = new ResetPasswordDTO();
        reset.setPassword("Secret@123");
        userService.resetPassword(admin, created.getId(), reset);
        AuditLogDO resetLog = audits(created.getUsername()).stream()
                .filter(item -> item.getSummary() != null && item.getSummary().contains("重置"))
                .findFirst()
                .orElseThrow();
        assertEquals("UPDATE", resetLog.getAction());
        assertFalse(resetLog.getSummary().contains("Secret@123"));

        int before = audits(admin.getUsername()).size();
        UserSaveDTO self = new UserSaveDTO();
        self.setRealName("自己");
        self.setRole(UserRole.ADMIN.name());
        self.setEnabled(false);
        assertThrows(BusinessException.class, () -> userService.update(admin, admin.getUserId(), self));
        assertEquals(before, audits(admin.getUsername()).size());
        assertEquals(1, sysUserDao.selectById(admin.getUserId()).getEnabled());
    }

    private List<AuditLogDO> audits(String username) {
        return auditLogDao.selectList(Wrappers.<AuditLogDO>lambdaQuery()
                .eq(AuditLogDO::getObjectNo, username)
                .orderByAsc(AuditLogDO::getId));
    }

    private UserSaveDTO newUser(String username, UserRole role) {
        UserSaveDTO dto = new UserSaveDTO();
        dto.setUsername(username + SEQ.getAndIncrement());
        dto.setRealName(username);
        dto.setEmail(dto.getUsername() + "@example.com");
        dto.setRole(role.name());
        dto.setPassword("Passw0rd!");
        dto.setEnabled(true);
        return dto;
    }

    private void disableAllSeedAdminsExcept(Long keepId) {
        sysUserDao.selectList(null).stream()
                .filter(user -> UserRole.ADMIN.name().equals(user.getRole()))
                .filter(user -> !keepId.equals(user.getId()))
                .forEach(user -> {
                    user.setEnabled(0);
                    sysUserDao.updateById(user);
                });
    }

    private SysUserDO insertUser(String username, UserRole role, int enabled) {
        SysUserDO user = new SysUserDO();
        user.setUsername(username + SEQ.getAndIncrement());
        user.setPasswordHash(passwordEncoder.encode("Passw0rd!"));
        user.setRealName(username);
        user.setEmail(username + "@example.com");
        user.setRole(role.name());
        user.setEnabled(enabled);
        user.setTokenVersion(0);
        user.setMustChangePassword(0);
        sysUserDao.insert(user);
        return user;
    }

    private AuthUser asUser(SysUserDO user) {
        return new AuthUser(user.getId(), user.getUsername(), user.getRole(), user.getTokenVersion());
    }
}
