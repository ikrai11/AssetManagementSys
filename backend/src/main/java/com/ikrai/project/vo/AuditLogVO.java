package com.ikrai.project.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AuditLogVO {

    private Long id;
    private Long operatorId;
    private String operatorName;
    private String module;
    private String moduleLabel;
    private String action;
    private String actionLabel;
    private String objectNo;
    private String summary;
    private LocalDateTime createdAt;
}
