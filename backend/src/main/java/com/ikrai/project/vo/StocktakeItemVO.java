package com.ikrai.project.vo;

import lombok.Data;

@Data
public class StocktakeItemVO {

    private Long id;
    private Long assetId;
    private String assetNo;
    private String assetName;
    private String assetStatus;
    private String assetStatusLabel;
    private String deptName;
    private String locationName;
    private String result;
    private String resultLabel;
    private String comment;
}
