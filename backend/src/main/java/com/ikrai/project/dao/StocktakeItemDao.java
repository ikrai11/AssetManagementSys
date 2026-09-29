package com.ikrai.project.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ikrai.project.dataobject.StocktakeItemDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface StocktakeItemDao extends BaseMapper<StocktakeItemDO> {
}
