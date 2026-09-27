package com.ikrai.project.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("borrow_log")
public class BorrowLogDO {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long borrowId;
    private String action;
    private Long operatorId;
    private String comment;
    private LocalDateTime createdAt;
}
