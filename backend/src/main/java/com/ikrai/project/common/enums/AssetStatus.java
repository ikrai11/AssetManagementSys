package com.ikrai.project.common.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum AssetStatus {
    IN_STOCK("在库"),
    PENDING("审批中"),
    BORROWED("已领用");

    private final String label;
}
