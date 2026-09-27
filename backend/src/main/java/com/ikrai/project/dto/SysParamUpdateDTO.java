package com.ikrai.project.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class SysParamUpdateDTO {

    @NotBlank(message = "提前提醒天数不能为空")
    private String remindLeadDays;

    @NotNull(message = "单次最长领用天数不能为空")
    @Min(value = 1, message = "单次最长领用天数必须在 1 到 365 之间")
    @Max(value = 365, message = "单次最长领用天数必须在 1 到 365 之间")
    private Integer borrowMaxDays;

    @NotNull(message = "续借上限不能为空")
    @Min(value = 1, message = "续借上限必须在 1 到 3650 之间")
    @Max(value = 3650, message = "续借上限必须在 1 到 3650 之间")
    private Integer renewMaxDaysFromIssue;

    @NotNull(message = "邮件通道不能为空")
    private Boolean mailChannelEnabled;

    @NotNull(message = "登录失败次数不能为空")
    @Min(value = 1, message = "登录失败次数必须在 1 到 20 之间")
    @Max(value = 20, message = "登录失败次数必须在 1 到 20 之间")
    private Integer loginMaxFailures;

    @NotNull(message = "锁定分钟数不能为空")
    @Min(value = 1, message = "锁定分钟数必须在 1 到 1440 之间")
    @Max(value = 1440, message = "锁定分钟数必须在 1 到 1440 之间")
    private Integer loginLockMinutes;
}
