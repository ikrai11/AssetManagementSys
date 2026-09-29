package com.ikrai.project.vo;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class BorrowOrderVO {

    private Long id;
    private String orderNo;
    private Long assetId;
    private String assetNo;
    private String assetName;
    private Long applicantId;
    private String applicantName;
    private String purpose;
    private LocalDate expectedReturnDate;
    private String remark;
    private String status;
    private String statusLabel;
    private boolean overdue;
    private Integer remainingDays;
    private Integer overdueDays;
    private Long approverId;
    private LocalDateTime approvedAt;
    private String approveComment;
    private LocalDateTime issuedAt;
    private LocalDateTime returnRequestedAt;
    private LocalDateTime returnedAt;
    private String returnComment;
    private Integer version;
    private LocalDateTime createdAt;
    private Long pendingRenewId;
    private LocalDate pendingRenewDate;
    private String pendingRenewReason;
    private List<BorrowLogVO> logs;
}
