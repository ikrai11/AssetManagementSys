package com.ikrai.project.service.user.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.ikrai.project.common.AuthUser;
import com.ikrai.project.common.exception.BusinessException;
import com.ikrai.project.config.JwtService;
import com.ikrai.project.dao.SysUserDao;
import com.ikrai.project.dataobject.SysUserDO;
import com.ikrai.project.dto.ChangePasswordDTO;
import com.ikrai.project.dto.LoginDTO;
import com.ikrai.project.manager.user.LoginLockManager;
import com.ikrai.project.service.user.AuthService;
import com.ikrai.project.vo.LoginVO;
import com.ikrai.project.vo.UserVO;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthServiceImpl implements AuthService {

    private static final String LOGIN_ERROR = "账号或密码错误";

    private final SysUserDao sysUserDao;
    private final PasswordEncoder passwordEncoder;
    private final LoginLockManager loginLockManager;
    private final JwtService jwtService;

    public AuthServiceImpl(SysUserDao sysUserDao,
                           PasswordEncoder passwordEncoder,
                           LoginLockManager loginLockManager,
                           JwtService jwtService) {
        this.sysUserDao = sysUserDao;
        this.passwordEncoder = passwordEncoder;
        this.loginLockManager = loginLockManager;
        this.jwtService = jwtService;
    }

    @Override
    public LoginVO login(LoginDTO dto) {
        loginLockManager.assertUnlocked(dto.getUsername());
        SysUserDO user = sysUserDao.selectOne(Wrappers.<SysUserDO>lambdaQuery()
                .eq(SysUserDO::getUsername, dto.getUsername()));
        if (user == null || user.getEnabled() == null || user.getEnabled() != 1
                || !passwordEncoder.matches(dto.getPassword(), user.getPasswordHash())) {
            loginLockManager.recordFailure(dto.getUsername());
            throw new BusinessException(LOGIN_ERROR);
        }
        loginLockManager.clear(dto.getUsername());
        AuthUser authUser = new AuthUser(user.getId(), user.getUsername(), user.getRole(), user.getTokenVersion());
        LoginVO vo = new LoginVO();
        vo.setUserId(user.getId());
        vo.setToken(jwtService.createToken(authUser));
        vo.setRole(user.getRole());
        vo.setRealName(user.getRealName());
        vo.setMustChangePassword(user.getMustChangePassword() != null && user.getMustChangePassword() == 1);
        return vo;
    }

    @Override
    public UserVO getMe(AuthUser current) {
        SysUserDO user = sysUserDao.selectById(current.getUserId());
        UserVO vo = new UserVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setRealName(user.getRealName());
        vo.setEmail(user.getEmail());
        vo.setMobile(user.getMobile());
        vo.setDeptId(user.getDeptId());
        vo.setRole(user.getRole());
        vo.setRoleLabel("ADMIN".equals(user.getRole()) ? "系统管理员" : "普通用户");
        vo.setEnabled(user.getEnabled() != null && user.getEnabled() == 1);
        vo.setMustChangePassword(user.getMustChangePassword() != null && user.getMustChangePassword() == 1);
        vo.setCreatedAt(user.getCreatedAt());
        return vo;
    }

    @Override
    @Transactional
    public void updatePassword(AuthUser current, ChangePasswordDTO dto) {
        SysUserDO user = sysUserDao.selectById(current.getUserId());
        if (!passwordEncoder.matches(dto.getOldPassword(), user.getPasswordHash())) {
            throw new BusinessException("原密码不正确");
        }
        user.setPasswordHash(passwordEncoder.encode(dto.getNewPassword()));
        user.setMustChangePassword(0);
        user.setTokenVersion(user.getTokenVersion() == null ? 1 : user.getTokenVersion() + 1);
        sysUserDao.updateById(user);
    }
}
