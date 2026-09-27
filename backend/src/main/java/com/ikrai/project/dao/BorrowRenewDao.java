package com.ikrai.project.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ikrai.project.dataobject.BorrowRenewDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

@Mapper
public interface BorrowRenewDao extends BaseMapper<BorrowRenewDO> {

    @Update("""
            UPDATE borrow_renew
            SET status = #{newStatus},
                approver_id = #{approverId},
                approved_at = #{approvedAt},
                approve_comment = #{comment},
                version = version + 1
            WHERE id = #{id} AND version = #{version} AND status = #{oldStatus}
            """)
    int casFinish(@Param("id") Long id,
                  @Param("version") Integer version,
                  @Param("oldStatus") String oldStatus,
                  @Param("newStatus") String newStatus,
                  @Param("approverId") Long approverId,
                  @Param("approvedAt") LocalDateTime approvedAt,
                  @Param("comment") String comment);
}
