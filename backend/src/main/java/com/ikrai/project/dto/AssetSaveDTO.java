package com.ikrai.project.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class AssetSaveDTO {

    @NotBlank(message = "资产编号不能为空")
    private String assetNo;
    @NotBlank(message = "资产名称不能为空")
    private String name;
    @NotNull(message = "设备类型不能为空")
    private Long categoryId;
    private String brand;
    private String model;
    private String serialNo;
    private LocalDate purchaseDate;
    private BigDecimal purchasePrice;
    private String supplier;
    private LocalDate warrantyUntil;
    private Long deptId;
    private Long locationId;
    private String remark;
}
