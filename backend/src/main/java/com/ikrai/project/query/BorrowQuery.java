package com.ikrai.project.query;

import lombok.Data;

@Data
public class BorrowQuery {

    private String status;
    private Integer page = 1;
    private Integer pageSize = 20;
}
