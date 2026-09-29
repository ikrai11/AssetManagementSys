package com.ikrai.project.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ikrai.project.dataobject.StocktakeDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

@Mapper
public interface StocktakeDao extends BaseMapper<StocktakeDO> {

    @Update("""
            UPDATE stocktake
            SET status = 'FINISHED',
                finished_at = #{finishedAt}
            WHERE id = #{id} AND status = 'OPEN'
            """)
    int finish(@Param("id") Long id, @Param("finishedAt") LocalDateTime finishedAt);
}
