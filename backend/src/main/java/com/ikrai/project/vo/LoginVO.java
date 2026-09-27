package com.ikrai.project.vo;

import lombok.Data;

@Data
public class LoginVO {

    private String token;
    private String role;
    private String realName;
    private boolean mustChangePassword;
}
