package com.ikrai.project.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("asset_category")
public class AssetCategoryDO {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private Integer enabled;
}
