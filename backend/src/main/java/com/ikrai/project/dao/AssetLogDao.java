package com.ikrai.project.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ikrai.project.dataobject.AssetLogDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AssetLogDao extends BaseMapper<AssetLogDO> {
}
