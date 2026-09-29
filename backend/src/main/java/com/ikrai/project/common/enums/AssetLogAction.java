package com.ikrai.project.common.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum AssetLogAction {
    TRANSFER("调拨"),
    REPAIR_START("送修"),
    REPAIR_FINISH("维修完成"),
    SCRAP("报废"),
    STOCKTAKE("盘点");

    private final String label;
}
