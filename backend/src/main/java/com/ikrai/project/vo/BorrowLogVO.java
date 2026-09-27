package com.ikrai.project.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class BorrowLogVO {

    private Long id;
    private String action;
    private Long operatorId;
    private String operatorName;
    private String comment;
    private LocalDateTime createdAt;
}
