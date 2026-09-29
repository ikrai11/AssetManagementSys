package com.ikrai.project.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AssetFileVO {

    private Long id;
    private Long assetId;
    private String kind;
    private String kindLabel;
    private String originalName;
    private String contentType;
    private Long sizeBytes;
    private LocalDateTime createdAt;
}
