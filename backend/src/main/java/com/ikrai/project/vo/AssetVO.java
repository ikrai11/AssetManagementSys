package com.ikrai.project.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AssetVO {

    private Long id;
    private String assetNo;
    private String name;
    private Long categoryId;
    private String categoryName;
    private String brand;
    private String model;
    private String serialNo;
    private String status;
    private String statusLabel;
    private LocalDate purchaseDate;
    private BigDecimal purchasePrice;
    private String supplier;
    private LocalDate warrantyUntil;
    private Long deptId;
    private String deptName;
    private Long locationId;
    private String locationName;
    private Long holderUserId;
    private String holderName;
    private LocalDate borrowStartDate;
    private LocalDate expectedReturnDate;
    private Long currentBorrowId;
    private String remark;
    private Integer version;
    private boolean overdue;
    private LocalDateTime updatedAt;
}
