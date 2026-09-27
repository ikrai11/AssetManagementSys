package com.ikrai.project.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ikrai.project.dataobject.BorrowLogDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface BorrowLogDao extends BaseMapper<BorrowLogDO> {
}
