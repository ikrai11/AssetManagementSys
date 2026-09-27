package com.ikrai.project.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_param")
public class SysParamDO {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String paramKey;
    private String paramValue;
    private LocalDateTime updatedAt;
}
