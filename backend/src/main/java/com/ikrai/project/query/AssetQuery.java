package com.ikrai.project.query;

import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
public class AssetQuery {

    private List<Long> categoryIds;
    private List<String> statuses;
    private LocalDate borrowStartFrom;
    private LocalDate borrowStartTo;
    private LocalDate dueFrom;
    private LocalDate dueTo;
    private String keyword;
    private Long holderUserId;
    private Long deptId;
    private Long locationId;
    private Integer page = 1;
    private Integer pageSize = 20;
}
