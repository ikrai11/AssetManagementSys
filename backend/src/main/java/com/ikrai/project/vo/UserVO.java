package com.ikrai.project.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class UserVO {

    private Long id;
    private String username;
    private String realName;
    private String email;
    private String mobile;
    private Long deptId;
    private String role;
    private String roleLabel;
    private String deptName;
    private boolean enabled;
    private boolean mustChangePassword;
    private LocalDateTime createdAt;
}
