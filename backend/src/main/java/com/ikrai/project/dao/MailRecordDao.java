package com.ikrai.project.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ikrai.project.dataobject.MailRecordDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface MailRecordDao extends BaseMapper<MailRecordDO> {
}
