package com.ikrai.project.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class OrgSaveDTO {

    @NotBlank(message = "名称不能为空")
    @Size(max = 64, message = "名称不能超过 64 字")
    private String name;
    private Long parentId;
    private Boolean enabled;
}
