package com.ikrai.project.dto;

import lombok.Data;

import java.util.List;

@Data
public class MessageReadDTO {

    private List<Long> ids;
    private Boolean all;
}
