package com.ikrai.project.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("asset_log")
public class AssetLogDO {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long assetId;
    private String action;
    private Long operatorId;
    private String comment;
    private LocalDateTime createdAt;
}
