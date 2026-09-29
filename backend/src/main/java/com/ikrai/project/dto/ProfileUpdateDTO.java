package com.ikrai.project.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ProfileUpdateDTO {

    @NotBlank(message = "姓名不能为空")
    @Size(max = 64, message = "姓名不能超过 64 字")
    private String realName;

    @Size(max = 128, message = "邮箱不能超过 128 字")
    private String email;
}
