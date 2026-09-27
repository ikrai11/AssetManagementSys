package com.ikrai.project.vo;

import lombok.Data;

@Data
public class UserVO {

    private Long id;
    private String username;
    private String realName;
    private String email;
    private String mobile;
    private Long deptId;
    private String role;
    private boolean mustChangePassword;
}
