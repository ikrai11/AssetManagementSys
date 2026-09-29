package com.ikrai.project.common.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum StocktakeStatus {
    OPEN("盘点中"),
    FINISHED("已结束");

    private final String label;
}
