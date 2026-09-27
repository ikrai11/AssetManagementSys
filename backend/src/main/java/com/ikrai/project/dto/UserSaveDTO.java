package com.ikrai.project.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UserSaveDTO {

    @Size(max = 64, message = "账号不能超过 64 字")
    private String username;
    @NotBlank(message = "姓名不能为空")
    @Size(max = 64, message = "姓名不能超过 64 字")
    private String realName;
    @Size(max = 128, message = "邮箱不能超过 128 字")
    private String email;
    @Size(max = 20, message = "手机不能超过 20 字")
    private String mobile;
    private Long deptId;
    @NotBlank(message = "角色不能为空")
    private String role;
    @Size(min = 8, message = "密码至少 8 位")
    private String password;
    private Boolean enabled;
}
