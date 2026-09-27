package com.ikrai.project.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class SiteMessageVO {

    private Long id;
    private String title;
    private String content;
    private String msgType;
    private String msgTypeLabel;
    private Long borrowId;
    private Boolean read;
    private LocalDateTime createdAt;
}
