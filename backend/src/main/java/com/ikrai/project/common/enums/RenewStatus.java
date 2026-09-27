package com.ikrai.project.common.enums;

import lombok.Getter;

@Getter
public enum RenewStatus {
    PENDING("待审批"),
    APPROVED("已通过"),
    REJECTED("已驳回");

    private final String label;

    RenewStatus(String label) {
        this.label = label;
    }
}
