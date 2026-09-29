package com.ikrai.project.service.user.impl;

import cn.hutool.core.lang.Validator;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ikrai.project.common.AuthUser;
import com.ikrai.project.common.enums.UserRole;
import com.ikrai.project.common.exception.BusinessException;
import com.ikrai.project.common.exception.NotFoundException;
import com.ikrai.project.dao.SysDeptDao;
import com.ikrai.project.dao.SysUserDao;
import com.ikrai.project.dataobject.SysDeptDO;
import com.ikrai.project.dataobject.SysUserDO;
import com.ikrai.project.dto.ResetPasswordDTO;
import com.ikrai.project.dto.UserSaveDTO;
import com.ikrai.project.query.UserQuery;
import com.ikrai.project.service.user.UserService;
import com.ikrai.project.vo.PageVO;
import com.ikrai.project.vo.UserVO;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

@Service
public class UserServiceImpl implements UserService {

    private final SysUserDao sysUserDao;
    private final SysDeptDao sysDeptDao;
    private final PasswordEncoder passwordEncoder;

    public UserServiceImpl(SysUserDao sysUserDao, SysDeptDao sysDeptDao, PasswordEncoder passwordEncoder) {
        this.sysUserDao = sysUserDao;
        this.sysDeptDao = sysDeptDao;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public UserVO create(UserSaveDTO dto) {
        if (StrUtil.isBlank(dto.getUsername())) {
            throw new BusinessException("账号不能为空");
        }
        if (StrUtil.isBlank(dto.getPassword())) {
            throw new BusinessException("密码不能为空");
        }
        assertUniqueUsername(dto.getUsername().trim(), null);
        SysUserDO user = new SysUserDO();
        user.setUsername(dto.getUsername().trim());
        user.setPasswordHash(passwordEncoder.encode(dto.getPassword()));
        user.setTokenVersion(0);
        user.setMustChangePassword(1);
        fillProfile(user, dto, true);
        sysUserDao.insert(user);
        return toVo(sysUserDao.selectById(user.getId()));
    }

    @Override
    @Transactional
    public UserVO update(AuthUser operator, Long id, UserSaveDTO dto) {
        SysUserDO user = requireUser(id);
        boolean willEnable = dto.getEnabled() == null ? user.getEnabled() != null && user.getEnabled() == 1 : dto.getEnabled();
        if (Objects.equals(operator.getUserId(), id) && user.getEnabled() != null && user.getEnabled() == 1 && !willEnable) {
            throw new BusinessException("不能停用自己");
        }
        String newRole = normalizeRole(dto.getRole());
        assertKeepEnabledAdmin(user, newRole, willEnable);
        boolean disabling = user.getEnabled() != null && user.getEnabled() == 1 && !willEnable;
        fillProfile(user, dto, false);
        if (disabling) {
            bumpToken(user);
        }
        sysUserDao.updateById(user);
        return toVo(sysUserDao.selectById(id));
    }

    @Override
    @Transactional
    public void resetPassword(AuthUser operator, Long id, ResetPasswordDTO dto) {
        SysUserDO user = requireUser(id);
        user.setPasswordHash(passwordEncoder.encode(dto.getPassword()));
        user.setMustChangePassword(1);
        bumpToken(user);
        sysUserDao.updateById(user);
    }

    @Override
    public PageVO<UserVO> list(UserQuery query) {
        int page = query.getPage() == null || query.getPage() < 1 ? 1 : query.getPage();
        int pageSize = query.getPageSize() == null ? 20 : Math.min(query.getPageSize(), 100);
        var wrapper = Wrappers.<SysUserDO>lambdaQuery();
        if (StrUtil.isNotBlank(query.getKeyword())) {
            String keyword = query.getKeyword().trim();
            wrapper.and(w -> w.like(SysUserDO::getUsername, keyword).or().like(SysUserDO::getRealName, keyword));
        }
        if (StrUtil.isNotBlank(query.getRole())) {
            wrapper.eq(SysUserDO::getRole, normalizeRole(query.getRole()));
        }
        if (query.getDeptId() != null) {
            wrapper.eq(SysUserDO::getDeptId, query.getDeptId());
        }
        if (query.getEnabled() != null) {
            wrapper.eq(SysUserDO::getEnabled, query.getEnabled() ? 1 : 0);
        }
        wrapper.orderByDesc(SysUserDO::getId);
        Page<SysUserDO> result = sysUserDao.selectPage(new Page<>(page, pageSize), wrapper);
        List<UserVO> list = result.getRecords().stream().map(this::toVo).toList();
        return new PageVO<>(list, result.getTotal(), page, pageSize);
    }

    private void fillProfile(SysUserDO user, UserSaveDTO dto, boolean creating) {
        String role = normalizeRole(dto.getRole());
        assertEmail(role, dto.getEmail());
        assertDept(dto.getDeptId(), creating ? null : user.getDeptId());
        user.setRealName(dto.getRealName().trim());
        user.setEmail(blankToNull(dto.getEmail()));
        user.setMobile(blankToNull(dto.getMobile()));
        user.setDeptId(dto.getDeptId());
        user.setRole(role);
        boolean enabled = dto.getEnabled() == null ? creating || (user.getEnabled() != null && user.getEnabled() == 1) : dto.getEnabled();
        user.setEnabled(enabled ? 1 : 0);
    }

    private void assertEmail(String role, String email) {
        if (UserRole.USER.name().equals(role) && StrUtil.isBlank(email)) {
            throw new BusinessException("普通用户邮箱不能为空");
        }
        if (StrUtil.isNotBlank(email) && !Validator.isEmail(email.trim())) {
            throw new BusinessException("邮箱格式不正确");
        }
    }

    private void assertDept(Long deptId, Long currentDeptId) {
        if (deptId == null || Objects.equals(deptId, currentDeptId)) {
            return;
        }
        SysDeptDO dept = sysDeptDao.selectById(deptId);
        if (dept == null) {
            throw new BusinessException("部门不存在");
        }
        if (dept.getEnabled() == null || dept.getEnabled() != 1) {
            throw new BusinessException("部门已停用，不能选用");
        }
    }

    private void assertUniqueUsername(String username, Long excludeId) {
        Long count = sysUserDao.selectCount(Wrappers.<SysUserDO>lambdaQuery()
                .eq(SysUserDO::getUsername, username)
                .ne(excludeId != null, SysUserDO::getId, excludeId));
        if (count != null && count > 0) {
            throw new BusinessException("账号已存在");
        }
    }

    private void assertKeepEnabledAdmin(SysUserDO current, String newRole, boolean willEnable) {
        boolean currentlyEnabledAdmin = UserRole.ADMIN.name().equals(current.getRole())
                && current.getEnabled() != null && current.getEnabled() == 1;
        boolean willBeEnabledAdmin = UserRole.ADMIN.name().equals(newRole) && willEnable;
        if (currentlyEnabledAdmin && !willBeEnabledAdmin && countEnabledAdmins() <= 1) {
            throw new BusinessException("至少保留一名启用中的管理员");
        }
    }

    private long countEnabledAdmins() {
        Long count = sysUserDao.selectCount(Wrappers.<SysUserDO>lambdaQuery()
                .eq(SysUserDO::getRole, UserRole.ADMIN.name())
                .eq(SysUserDO::getEnabled, 1));
        return count == null ? 0 : count;
    }

    private String normalizeRole(String role) {
        if (UserRole.ADMIN.name().equals(role) || UserRole.USER.name().equals(role)) {
            return role;
        }
        throw new BusinessException("角色只能是系统管理员或普通用户");
    }

    private void bumpToken(SysUserDO user) {
        user.setTokenVersion(user.getTokenVersion() == null ? 1 : user.getTokenVersion() + 1);
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

    private String blankToNull(String value) {
        return StrUtil.isBlank(value) ? null : value.trim();
    }
}
