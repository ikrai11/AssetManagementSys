package com.ikrai.project.common.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum MailStatus {
    PENDING("待发送"),
    SENT("已发送"),
    FAILED("发送失败"),
    SKIPPED("未发送");

    private final String label;
}
