package com.ikrai.project.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AssetTransferDTO {

    private Long deptId;
    private Long locationId;
    @NotBlank(message = "调拨原因不能为空")
    private String reason;
}
