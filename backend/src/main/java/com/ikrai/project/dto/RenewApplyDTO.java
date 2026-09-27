package com.ikrai.project.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

@Data
public class RenewApplyDTO {

    @NotNull(message = "新预计归还日不能为空")
    private LocalDate newReturnDate;

    @NotBlank(message = "续借理由不能为空")
    private String reason;
}
