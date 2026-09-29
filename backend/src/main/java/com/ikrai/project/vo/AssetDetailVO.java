package com.ikrai.project.vo;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;
import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
public class AssetDetailVO extends AssetVO {

    private List<BorrowLogVO> logs;
    private List<AssetLogVO> lifecycleLogs;
    private LocalDate repairSentDate;
    private String repairFault;
}
