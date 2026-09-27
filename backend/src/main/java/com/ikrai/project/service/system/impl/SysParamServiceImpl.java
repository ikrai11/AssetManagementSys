package com.ikrai.project.service.system.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.ikrai.project.common.AuthUser;
import com.ikrai.project.common.ParamKeys;
import com.ikrai.project.common.exception.BusinessException;
import com.ikrai.project.dao.AuditLogDao;
import com.ikrai.project.dao.SysParamDao;
import com.ikrai.project.dataobject.AuditLogDO;
import com.ikrai.project.dataobject.SysParamDO;
import com.ikrai.project.dto.SysParamUpdateDTO;
import com.ikrai.project.service.system.SysParamService;
import com.ikrai.project.vo.SysParamVO;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class SysParamServiceImpl implements SysParamService {

    private final SysParamDao sysParamDao;
    private final AuditLogDao auditLogDao;

    public SysParamServiceImpl(SysParamDao sysParamDao, AuditLogDao auditLogDao) {
        this.sysParamDao = sysParamDao;
        this.auditLogDao = auditLogDao;
    }

    @Override
    public SysParamVO get() {
        return toVo(loadAll());
    }

    @Override
    @Transactional
    public SysParamVO update(AuthUser operator, SysParamUpdateDTO dto) {
        if (!operator.isAdmin()) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "无权限");
        }
        String leadDays = normalizeLeadDays(dto.getRemindLeadDays());
        Map<String, SysParamDO> rows = loadAll();
        String mailValue = Boolean.TRUE.equals(dto.getMailChannelEnabled()) ? "true" : "false";
        save(rows, ParamKeys.REMIND_LEAD_DAYS, leadDays);
        save(rows, ParamKeys.BORROW_MAX_DAYS, String.valueOf(dto.getBorrowMaxDays()));
        save(rows, ParamKeys.RENEW_MAX_DAYS, String.valueOf(dto.getRenewMaxDaysFromIssue()));
        save(rows, ParamKeys.MAIL_CHANNEL, mailValue);
        save(rows, ParamKeys.LOGIN_MAX_FAILURES, String.valueOf(dto.getLoginMaxFailures()));
        save(rows, ParamKeys.LOGIN_LOCK_MINUTES, String.valueOf(dto.getLoginLockMinutes()));
        AuditLogDO log = new AuditLogDO();
        log.setOperatorId(operator.getUserId());
        log.setModule("PARAM");
        log.setAction("UPDATE");
        log.setObjectNo("system");
        log.setSummary("更新系统参数：最长领用 " + dto.getBorrowMaxDays()
                + " 天，续借上限 " + dto.getRenewMaxDaysFromIssue()
                + " 天，提前提醒 " + leadDays);
        auditLogDao.insert(log);
        return get();
    }

    @Override
    public int borrowMaxDays() {
        return readInt(ParamKeys.BORROW_MAX_DAYS);
    }

    @Override
    public int renewMaxDaysFromIssue() {
        return readInt(ParamKeys.RENEW_MAX_DAYS);
    }

    @Override
    public List<Integer> remindLeadDays() {
        return parseLeadDays(read(ParamKeys.REMIND_LEAD_DAYS));
    }

    @Override
    public boolean mailChannelEnabled() {
        return Boolean.parseBoolean(read(ParamKeys.MAIL_CHANNEL));
    }

    @Override
    public int loginMaxFailures() {
        return readInt(ParamKeys.LOGIN_MAX_FAILURES);
    }

    @Override
    public int loginLockMinutes() {
        return readInt(ParamKeys.LOGIN_LOCK_MINUTES);
    }

    private String normalizeLeadDays(String raw) {
        return parseLeadDays(raw).stream().map(String::valueOf).collect(Collectors.joining(","));
    }

    private List<Integer> parseLeadDays(String raw) {
        if (StrUtil.isBlank(raw)) {
            throw new BusinessException("提前提醒天数不能为空");
        }
        LinkedHashSet<Integer> days = new LinkedHashSet<>();
        for (String part : raw.split(",")) {
            String token = part.trim();
            if (token.isEmpty()) {
                throw new BusinessException("提前提醒天数格式不正确");
            }
            int day;
            try {
                day = Integer.parseInt(token);
            } catch (NumberFormatException ex) {
                throw new BusinessException("提前提醒天数格式不正确");
            }
            if (day == 0) {
                throw new BusinessException("到期当天始终提醒，无需写入提前提醒天数");
            }
            if (day < 1) {
                throw new BusinessException("提前提醒天数必须是正整数");
            }
            if (!days.add(day)) {
                throw new BusinessException("提前提醒天数不能重复");
            }
        }
        return new ArrayList<>(days);
    }

    private Map<String, SysParamDO> loadAll() {
        return sysParamDao.selectList(null).stream()
                .collect(Collectors.toMap(SysParamDO::getParamKey, row -> row));
    }

    private void save(Map<String, SysParamDO> rows, String key, String value) {
        SysParamDO row = rows.get(key);
        if (row == null) {
            throw new BusinessException("系统参数不存在：" + key);
        }
        row.setParamValue(value);
        sysParamDao.updateById(row);
    }

    private SysParamVO toVo(Map<String, SysParamDO> rows) {
        SysParamVO vo = new SysParamVO();
        vo.setRemindLeadDays(value(rows, ParamKeys.REMIND_LEAD_DAYS));
        vo.setBorrowMaxDays(Integer.parseInt(value(rows, ParamKeys.BORROW_MAX_DAYS)));
        vo.setRenewMaxDaysFromIssue(Integer.parseInt(value(rows, ParamKeys.RENEW_MAX_DAYS)));
        vo.setMailChannelEnabled(Boolean.parseBoolean(value(rows, ParamKeys.MAIL_CHANNEL)));
        vo.setLoginMaxFailures(Integer.parseInt(value(rows, ParamKeys.LOGIN_MAX_FAILURES)));
        vo.setLoginLockMinutes(Integer.parseInt(value(rows, ParamKeys.LOGIN_LOCK_MINUTES)));
        return vo;
    }

    private String value(Map<String, SysParamDO> rows, String key) {
        SysParamDO row = rows.get(key);
        if (row == null || StrUtil.isBlank(row.getParamValue())) {
            throw new BusinessException("系统参数不存在：" + key);
        }
        return row.getParamValue();
    }

    private String read(String key) {
        SysParamDO row = sysParamDao.selectOne(Wrappers.<SysParamDO>lambdaQuery()
                .eq(SysParamDO::getParamKey, key));
        if (row == null || StrUtil.isBlank(row.getParamValue())) {
            throw new BusinessException("系统参数不存在：" + key);
        }
        return row.getParamValue();
    }

    private int readInt(String key) {
        try {
            return Integer.parseInt(read(key));
        } catch (NumberFormatException ex) {
            throw new BusinessException("系统参数格式不正确：" + key);
        }
    }
}
