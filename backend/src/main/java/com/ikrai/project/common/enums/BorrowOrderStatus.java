package com.ikrai.project.common.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum BorrowOrderStatus {
    DRAFT("草稿"),
    PENDING("待审批"),
    APPROVED("已通过"),
    REJECTED("已驳回"),
    WITHDRAWN("已撤回"),
    BORROWING("领用中"),
    RETURN_PENDING("待归还确认"),
    RETURNED("已归还");

    private final String label;

    public boolean isActive() {
        return this == DRAFT || this == PENDING || this == APPROVED
                || this == BORROWING || this == RETURN_PENDING;
    }
}
