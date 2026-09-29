package com.ikrai.project.service.user.impl;

import cn.hutool.core.lang.Validator;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.ikrai.project.common.AuthUser;
import com.ikrai.project.common.enums.UserRole;
import com.ikrai.project.common.exception.BusinessException;
import com.ikrai.project.common.exception.NotFoundException;
import com.ikrai.project.config.JwtService;
import com.ikrai.project.dao.AuditLogDao;
import com.ikrai.project.dao.SysDeptDao;
import com.ikrai.project.dao.SysUserDao;
import com.ikrai.project.dataobject.AuditLogDO;
import com.ikrai.project.dataobject.SysDeptDO;
import com.ikrai.project.dataobject.SysUserDO;
import com.ikrai.project.dto.ChangePasswordDTO;
import com.ikrai.project.dto.LoginDTO;
import com.ikrai.project.dto.ProfileUpdateDTO;
import com.ikrai.project.manager.user.LoginLockManager;
import com.ikrai.project.service.user.AuthService;
import com.ikrai.project.vo.LoginVO;
import com.ikrai.project.vo.UserVO;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
public class AuthServiceImpl implements AuthService {

    private static final String LOGIN_ERROR = "账号或密码错误";

    private final SysUserDao sysUserDao;
    private final SysDeptDao sysDeptDao;
    private final AuditLogDao auditLogDao;
    private final PasswordEncoder passwordEncoder;
    private final LoginLockManager loginLockManager;
    private final JwtService jwtService;

    public AuthServiceImpl(SysUserDao sysUserDao,
                           SysDeptDao sysDeptDao,
                           AuditLogDao auditLogDao,
                           PasswordEncoder passwordEncoder,
                           LoginLockManager loginLockManager,
                           JwtService jwtService) {
        this.sysUserDao = sysUserDao;
        this.sysDeptDao = sysDeptDao;
        this.auditLogDao = auditLogDao;
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
        return toVo(requireUser(current.getUserId()));
    }

    @Override
    @Transactional
    public UserVO updateProfile(AuthUser current, ProfileUpdateDTO dto) {
        SysUserDO user = requireUser(current.getUserId());
        String newName = dto.getRealName().trim();
        if (newName.isEmpty()) {
            throw new BusinessException("姓名不能为空");
        }
        String newEmail = blankToNull(dto.getEmail());
        assertEmail(user.getRole(), newEmail);
        boolean nameChanged = !newName.equals(StrUtil.nullToEmpty(user.getRealName()));
        boolean emailChanged = !Objects.equals(newEmail, blankToNull(user.getEmail()));
        if (!nameChanged && !emailChanged) {
            return toVo(user);
        }
        String oldName = user.getRealName();
        sysUserDao.update(null, Wrappers.<SysUserDO>lambdaUpdate()
                .eq(SysUserDO::getId, user.getId())
                .set(SysUserDO::getRealName, newName)
                .set(SysUserDO::getEmail, newEmail));
        writeAudit(current, user.getUsername(), profileSummary(oldName, newName, nameChanged, emailChanged));
        return toVo(sysUserDao.selectById(user.getId()));
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

    private SysUserDO requireUser(Long id) {
        SysUserDO user = sysUserDao.selectById(id);
        if (user == null) {
            throw new NotFoundException("用户不存在");
        }
        return user;
    }

    private UserVO toVo(SysUserDO user) {
        UserVO vo = new UserVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setRealName(user.getRealName());
        vo.setEmail(user.getEmail());
        vo.setMobile(user.getMobile());
        vo.setDeptId(user.getDeptId());
        if (user.getDeptId() != null) {
            SysDeptDO dept = sysDeptDao.selectById(user.getDeptId());
            vo.setDeptName(dept == null ? null : dept.getName());
        }
        vo.setRole(user.getRole());
        vo.setRoleLabel(UserRole.ADMIN.name().equals(user.getRole()) ? "系统管理员" : "普通用户");
        vo.setEnabled(user.getEnabled() != null && user.getEnabled() == 1);
        vo.setMustChangePassword(user.getMustChangePassword() != null && user.getMustChangePassword() == 1);
        vo.setCreatedAt(user.getCreatedAt());
        return vo;
    }

    private void assertEmail(String role, String email) {
        if (UserRole.USER.name().equals(role) && email == null) {
            throw new BusinessException("普通用户邮箱不能为空");
        }
        if (email != null && !Validator.isEmail(email)) {
            throw new BusinessException("邮箱格式不正确");
        }
    }

    private String profileSummary(String oldName, String newName, boolean nameChanged, boolean emailChanged) {
        List<String> parts = new ArrayList<>();
        if (nameChanged) {
            parts.add("姓名由「" + StrUtil.nullToEmpty(oldName) + "」改为「" + newName + "」");
        }
        if (emailChanged) {
            parts.add("邮箱已更新");
        }
        return "修改个人资料：" + String.join("；", parts);
    }

    private void writeAudit(AuthUser operator, String username, String summary) {
        AuditLogDO log = new AuditLogDO();
        log.setOperatorId(operator.getUserId());
        log.setModule("USER");
        log.setAction("UPDATE");
        log.setObjectNo(username);
        log.setSummary(StrUtil.sub(summary, 0, 500));
        auditLogDao.insert(log);
    }

    private String blankToNull(String value) {
        return StrUtil.trimToNull(value);
    }
}
