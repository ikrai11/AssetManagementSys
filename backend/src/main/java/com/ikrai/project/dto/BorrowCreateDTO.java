package com.ikrai.project.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

@Data
public class BorrowCreateDTO {

    @NotNull(message = "设备不能为空")
    private Long assetId;
    private String purpose;
    private LocalDate expectedReturnDate;
    private String remark;
    private boolean submit;
}
