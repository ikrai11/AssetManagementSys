package com.ikrai.project.common.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum MessageType {
    PENDING_APPROVAL("待审批"),
    APPROVAL_RESULT("审批结果"),
    ISSUE("发放"),
    RETURN_CONFIRM("归还确认"),
    RENEW_RESULT("续借结果"),
    DUE_REMIND("到期提醒"),
    OVERDUE_REMIND("逾期提醒"),
    IMPORT_RESULT("导入结果");

    private final String label;
}
