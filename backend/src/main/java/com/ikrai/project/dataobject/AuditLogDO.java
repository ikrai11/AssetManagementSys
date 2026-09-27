package com.ikrai.project.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("audit_log")
public class AuditLogDO {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long operatorId;
    private String module;
    private String action;
    private String objectNo;
    private String summary;
    private LocalDateTime createdAt;
}
