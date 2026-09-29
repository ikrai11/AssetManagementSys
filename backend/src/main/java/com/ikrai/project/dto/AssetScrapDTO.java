package com.ikrai.project.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AssetScrapDTO {

    @NotBlank(message = "报废原因不能为空")
    private String reason;
}
