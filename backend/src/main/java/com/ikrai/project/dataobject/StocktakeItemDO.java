package com.ikrai.project.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("stocktake_item")
public class StocktakeItemDO {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long stocktakeId;
    private Long assetId;
    private String assetNo;
    private String assetName;
    private String assetStatus;
    private Long deptId;
    private Long locationId;
    private String result;
    private String comment;
}
