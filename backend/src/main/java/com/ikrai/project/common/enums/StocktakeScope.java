package com.ikrai.project.common.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum StocktakeScope {
    DEPT("部门"),
    LOCATION("地点");

    private final String label;
}
