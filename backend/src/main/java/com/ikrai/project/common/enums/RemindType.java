package com.ikrai.project.common.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum RemindType {
    DUE_SOON("即将到期"),
    DUE_TODAY("到期当天"),
    OVERDUE("已逾期");

    private final String label;
}
