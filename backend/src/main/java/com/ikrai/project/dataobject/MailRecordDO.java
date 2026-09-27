package com.ikrai.project.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("mail_record")
public class MailRecordDO {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long receiverId;
    private String email;
    private String subject;
    private String content;
    private Long borrowId;
    private String status;
    private String failReason;
    private Integer retryCount;
    private LocalDateTime sentAt;
    private LocalDateTime createdAt;
}
