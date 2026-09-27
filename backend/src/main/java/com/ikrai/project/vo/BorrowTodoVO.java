package com.ikrai.project.vo;

import lombok.Data;

import java.util.List;

@Data
public class BorrowTodoVO {

    private List<BorrowOrderVO> pending;
    private List<BorrowOrderVO> approved;
    private List<BorrowOrderVO> returnPending;
    private List<BorrowRenewVO> renewPending;
}
