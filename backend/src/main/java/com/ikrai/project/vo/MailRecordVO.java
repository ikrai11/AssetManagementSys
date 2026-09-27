package com.ikrai.project.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class MailRecordVO {

    private Long id;
    private Long receiverId;
    private String receiverName;
    private String email;
    private String subject;
    private String content;
    private Long borrowId;
    private String status;
    private String statusLabel;
    private String failReason;
    private Integer retryCount;
    private LocalDateTime sentAt;
    private LocalDateTime createdAt;
}
