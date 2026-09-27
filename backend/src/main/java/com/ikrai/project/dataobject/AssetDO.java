package com.ikrai.project.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("asset")
public class AssetDO {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String assetNo;
    private String name;
    private Long categoryId;
    private String brand;
    private String model;
    private String serialNo;
    private String status;
    private LocalDate purchaseDate;
    private BigDecimal purchasePrice;
    private String supplier;
    private LocalDate warrantyUntil;
    private Long deptId;
    private Long locationId;
    private Long holderUserId;
    private LocalDate borrowStartDate;
    private LocalDate expectedReturnDate;
    private Long currentBorrowId;
    private String remark;
    @Version
    private Integer version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
