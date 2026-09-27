package com.ikrai.project.vo;

import lombok.Data;

@Data
public class StatsOverviewVO {

    private long total;
    private long inStock;
    private long borrowed;
    private long pending;
    private long repairing;
    private long scrapped;
    private long dueSoon;
    private long overdue;
    private long pendingApproval;
    private long myApplying;
    private long myUsing;
    private long myDueSoon;
    private long myOverdue;
}
