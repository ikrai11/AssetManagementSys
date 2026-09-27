package com.ikrai.project.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("borrow_order")
public class BorrowOrderDO {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String orderNo;
    private Long assetId;
    private Long applicantId;
    private String purpose;
    private LocalDate expectedReturnDate;
    private String remark;
    private String status;
    private Long approverId;
    private LocalDateTime approvedAt;
    private String approveComment;
    private LocalDateTime issuedAt;
    private LocalDateTime returnRequestedAt;
    private LocalDateTime returnedAt;
    private String returnComment;
    @Version
    private Integer version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
