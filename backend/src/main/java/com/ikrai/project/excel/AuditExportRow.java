package com.ikrai.project.excel;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.Data;

@Data
public class AuditExportRow {

    @ExcelProperty("时间")
    private String createdAt;
    @ExcelProperty("操作者")
    private String operatorName;
    @ExcelProperty("模块")
    private String moduleLabel;
    @ExcelProperty("动作")
    private String actionLabel;
    @ExcelProperty("对象编号")
    private String objectNo;
    @ExcelProperty("变更摘要")
    private String summary;
}
