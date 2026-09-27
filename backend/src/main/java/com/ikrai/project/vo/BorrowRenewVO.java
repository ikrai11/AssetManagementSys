package com.ikrai.project.vo;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class BorrowRenewVO {

    private Long id;
    private Long borrowId;
    private String orderNo;
    private String assetNo;
    private String assetName;
    private String applicantName;
    private LocalDate oldReturnDate;
    private LocalDate newReturnDate;
    private String reason;
    private String status;
    private String statusLabel;
    private String approveComment;
    private LocalDateTime createdAt;
}
