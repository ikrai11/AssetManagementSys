package com.ikrai.project.query;

import lombok.Data;

@Data
public class MessageQuery {

    private String box;
    private String msgType;
    private Integer page;
    private Integer pageSize;
}
