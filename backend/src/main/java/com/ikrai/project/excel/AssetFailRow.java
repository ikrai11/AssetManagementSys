package com.ikrai.project.excel;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.Data;

@Data
public class AssetFailRow {

    @ExcelProperty("行号")
    private Integer rowNum;
    @ExcelProperty("资产编号")
    private String assetNo;
    @ExcelProperty("原因")
    private String reason;
}
