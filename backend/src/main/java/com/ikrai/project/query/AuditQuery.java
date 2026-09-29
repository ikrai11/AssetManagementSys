package com.ikrai.project.query;

import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

@Data
public class AuditQuery {

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate from;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate to;
    private String module;
    private String objectNo;
    private String operator;
    private Integer page = 1;
    private Integer pageSize = 20;
}
