package com.ikrai.project.vo;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class AssetFileContent {

    private final String originalName;
    private final String contentType;
    private final byte[] bytes;
}
