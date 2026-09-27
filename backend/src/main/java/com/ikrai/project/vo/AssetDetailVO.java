package com.ikrai.project.vo;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
public class AssetDetailVO extends AssetVO {

    private List<BorrowLogVO> logs;
}
