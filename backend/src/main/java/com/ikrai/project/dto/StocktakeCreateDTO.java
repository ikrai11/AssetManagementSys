package com.ikrai.project.dto;

import lombok.Data;

@Data
public class StocktakeCreateDTO {

    private String scopeType;
    private Long deptId;
    private Long locationId;
}
