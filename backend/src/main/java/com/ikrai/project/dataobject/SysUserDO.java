package com.ikrai.project.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_user")
public class SysUserDO {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String username;
    private String passwordHash;
    private String realName;
    private String email;
    private String mobile;
    private Long deptId;
    private String role;
    private Integer enabled;
    private Integer tokenVersion;
    private Integer mustChangePassword;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
