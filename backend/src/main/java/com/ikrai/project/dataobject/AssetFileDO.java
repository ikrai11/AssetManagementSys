package com.ikrai.project.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("asset_file")
public class AssetFileDO {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long assetId;
    private String kind;
    private String originalName;
    private String storedName;
    private String contentType;
    private Long sizeBytes;
    private Long uploadedBy;
    private LocalDateTime createdAt;
}
