package com.ikrai.project.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("stocktake")
public class StocktakeDO {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String title;
    private String scopeType;
    private Long deptId;
    private Long locationId;
    private String status;
    private Long operatorId;
    private LocalDateTime finishedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
