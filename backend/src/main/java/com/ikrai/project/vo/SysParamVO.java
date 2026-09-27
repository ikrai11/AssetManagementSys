package com.ikrai.project.vo;

import lombok.Data;

@Data
public class SysParamVO {

    private String remindLeadDays;
    private int borrowMaxDays;
    private int renewMaxDaysFromIssue;
    private boolean mailChannelEnabled;
    private int loginMaxFailures;
    private int loginLockMinutes;
}
