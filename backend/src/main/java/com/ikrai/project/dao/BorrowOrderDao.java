package com.ikrai.project.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ikrai.project.dataobject.BorrowOrderDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface BorrowOrderDao extends BaseMapper<BorrowOrderDO> {

    @Select("SELECT COUNT(*) FROM borrow_order WHERE order_no LIKE CONCAT(#{prefix}, '%')")
    long countByOrderNoPrefix(@Param("prefix") String prefix);

    @Update("""
            UPDATE borrow_order
            SET status = #{newStatus},
                version = version + 1
            WHERE id = #{id} AND version = #{version} AND status = #{oldStatus}
            """)
    int casUpdateStatus(@Param("id") Long id,
                        @Param("version") Integer version,
                        @Param("oldStatus") String oldStatus,
                        @Param("newStatus") String newStatus);
}
