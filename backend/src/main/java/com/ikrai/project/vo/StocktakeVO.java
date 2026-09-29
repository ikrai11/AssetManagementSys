package com.ikrai.project.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class StocktakeVO {

    private Long id;
    private String title;
    private String scopeType;
    private String scopeLabel;
    private Long deptId;
    private Long locationId;
    private String status;
    private String statusLabel;
    private Integer total;
    private Integer pending;
    private Integer difference;
    private LocalDateTime createdAt;
    private LocalDateTime finishedAt;
    private List<StocktakeItemVO> items;
}
