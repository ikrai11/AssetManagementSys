package com.ikrai.project.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("reminder_mark")
public class ReminderMarkDO {

    @TableId(type = IdType.AUTO)
    private Long id;
    private LocalDate bizDate;
    private String remindType;
    private Long borrowId;
    private Long receiverId;
    private LocalDateTime createdAt;
}
