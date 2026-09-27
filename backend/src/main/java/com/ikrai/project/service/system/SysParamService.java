package com.ikrai.project.service.system;

import com.ikrai.project.common.AuthUser;
import com.ikrai.project.dto.SysParamUpdateDTO;
import com.ikrai.project.vo.SysParamVO;

import java.util.List;

public interface SysParamService {

    SysParamVO get();

    SysParamVO update(AuthUser operator, SysParamUpdateDTO dto);

    int borrowMaxDays();

    int renewMaxDaysFromIssue();

    List<Integer> remindLeadDays();

    boolean mailChannelEnabled();

    int loginMaxFailures();

    int loginLockMinutes();
}
