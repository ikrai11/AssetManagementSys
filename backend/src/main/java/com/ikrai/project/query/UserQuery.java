package com.ikrai.project.query;

import lombok.Data;

@Data
public class UserQuery {

    private String keyword;
    private String role;
    private Long deptId;
    private Boolean enabled;
    private Integer page = 1;
    private Integer pageSize = 20;
}
