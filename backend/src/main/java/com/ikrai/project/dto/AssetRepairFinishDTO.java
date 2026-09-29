package com.ikrai.project.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.LocalDate;

@Data
public class AssetRepairFinishDTO {

    @NotBlank(message = "维修结果不能为空")
    private String result;
    private LocalDate finishedDate;
}
