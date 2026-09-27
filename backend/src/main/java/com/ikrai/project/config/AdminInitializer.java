package com.ikrai.project.config;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.ikrai.project.common.enums.UserRole;
import com.ikrai.project.dao.SysUserDao;
import com.ikrai.project.dataobject.SysUserDO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class AdminInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminInitializer.class);

    private final SysUserDao sysUserDao;
    private final PasswordEncoder passwordEncoder;
    private final AmsProperties properties;

    public AdminInitializer(SysUserDao sysUserDao, PasswordEncoder passwordEncoder, AmsProperties properties) {
        this.sysUserDao = sysUserDao;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
    }

    @Override
    public void run(String... args) {
        ensureUser("admin", "系统管理员", UserRole.ADMIN, properties.getInit().getAdminPassword(), 1);
        ensureUser("user", "普通用户", UserRole.USER, properties.getInit().getUserPassword(), 0);
    }

    private void ensureUser(String username, String realName, UserRole role, String rawPassword, int mustChange) {
        Long count = sysUserDao.selectCount(Wrappers.<SysUserDO>lambdaQuery().eq(SysUserDO::getUsername, username));
        if (count != null && count > 0) {
            return;
        }
        SysUserDO user = new SysUserDO();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        user.setRealName(realName);
        user.setEmail(username + "@local.dev");
        user.setRole(role.name());
        user.setEnabled(1);
        user.setTokenVersion(0);
        user.setMustChangePassword(mustChange);
        sysUserDao.insert(user);
        log.info("已初始化账号 {}，密码 {}。首次登录后请修改密码。", username, rawPassword);
    }
}
