package com.ikrai.project.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("asset_repair")
public class AssetRepairDO {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long assetId;
    private String fault;
    private LocalDate sentDate;
    private LocalDate finishedDate;
    private String resultText;
    private String status;
    private Long operatorId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
