package com.ikrai.project.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ikrai.project.dataobject.AssetDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDate;

@Mapper
public interface AssetDao extends BaseMapper<AssetDO> {

    @Update("""
            UPDATE asset
            SET status = #{newStatus},
                current_borrow_id = #{currentBorrowId},
                version = version + 1
            WHERE id = #{id} AND version = #{version} AND status = #{oldStatus}
            """)
    int casOccupy(@Param("id") Long id,
                  @Param("version") Integer version,
                  @Param("oldStatus") String oldStatus,
                  @Param("newStatus") String newStatus,
                  @Param("currentBorrowId") Long currentBorrowId);

    @Update("""
            UPDATE asset
            SET status = #{newStatus},
                current_borrow_id = NULL,
                version = version + 1
            WHERE id = #{id} AND version = #{version} AND status = #{oldStatus}
            """)
    int casRelease(@Param("id") Long id,
                   @Param("version") Integer version,
                   @Param("oldStatus") String oldStatus,
                   @Param("newStatus") String newStatus);

    @Update("""
            UPDATE asset
            SET status = #{newStatus},
                holder_user_id = #{holderUserId},
                borrow_start_date = #{borrowStartDate},
                expected_return_date = #{expectedReturnDate},
                version = version + 1
            WHERE id = #{id} AND version = #{version} AND status = #{oldStatus}
            """)
    int casIssue(@Param("id") Long id,
                 @Param("version") Integer version,
                 @Param("oldStatus") String oldStatus,
                 @Param("newStatus") String newStatus,
                 @Param("holderUserId") Long holderUserId,
                 @Param("borrowStartDate") LocalDate borrowStartDate,
                 @Param("expectedReturnDate") LocalDate expectedReturnDate);

    @Update("""
            UPDATE asset
            SET status = #{newStatus},
                holder_user_id = NULL,
                borrow_start_date = NULL,
                expected_return_date = NULL,
                current_borrow_id = NULL,
                version = version + 1
            WHERE id = #{id} AND version = #{version} AND status = #{oldStatus}
            """)
    int casReturn(@Param("id") Long id,
                  @Param("version") Integer version,
                  @Param("oldStatus") String oldStatus,
                  @Param("newStatus") String newStatus);
}
