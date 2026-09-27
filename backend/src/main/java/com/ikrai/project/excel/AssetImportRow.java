package com.ikrai.project.excel;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.Data;

@Data
public class AssetImportRow {

    @ExcelProperty("资产编号")
    private String assetNo;
    @ExcelProperty("资产名称")
    private String name;
    @ExcelProperty("设备类型")
    private String categoryName;
    @ExcelProperty("品牌")
    private String brand;
    @ExcelProperty("型号")
    private String model;
    @ExcelProperty("序列号")
    private String serialNo;
    @ExcelProperty("购置日期")
    private String purchaseDate;
    @ExcelProperty("购置价格")
    private String purchasePrice;
    @ExcelProperty("供应商")
    private String supplier;
    @ExcelProperty("保修截止日期")
    private String warrantyUntil;
    @ExcelProperty("责任部门")
    private String deptName;
    @ExcelProperty("存放地点")
    private String locationName;
    @ExcelProperty("备注")
    private String remark;
}
