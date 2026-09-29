package com.ikrai.project.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.LocalDate;

@Data
public class AssetRepairStartDTO {

    @NotBlank(message = "故障说明不能为空")
    private String fault;
    private LocalDate sentDate;
}
