package com.ikrai.project.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("borrow_renew")
public class BorrowRenewDO {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long borrowId;
    private LocalDate oldReturnDate;
    private LocalDate newReturnDate;
    private String reason;
    private String status;
    private Long approverId;
    private LocalDateTime approvedAt;
    private String approveComment;
    @Version
    private Integer version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
