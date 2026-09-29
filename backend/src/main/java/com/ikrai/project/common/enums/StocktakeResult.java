package com.ikrai.project.common.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum StocktakeResult {
    PENDING("未盘"),
    NORMAL("正常"),
    MISSING("缺失"),
    MISMATCH("位置不符");

    private final String label;
}
