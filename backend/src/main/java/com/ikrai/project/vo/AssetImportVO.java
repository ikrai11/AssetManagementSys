package com.ikrai.project.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class AssetImportVO {

    private int successCount;
    private int failCount;
    private List<AssetImportFailVO> failures = new ArrayList<>();
}
