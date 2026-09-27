package com.ikrai.project.manager.borrow;

import com.ikrai.project.common.enums.BorrowLogAction;
import com.ikrai.project.dataobject.BorrowOrderDO;

public interface BorrowManager {

    void insertNew(BorrowOrderDO order);

    BorrowOrderDO getById(Long id);

    boolean hasActiveOrder(Long assetId, Long excludeOrderId);

    void saveLog(Long borrowId, BorrowLogAction action, Long operatorId, String comment);

    void casUpdateStatus(BorrowOrderDO order, String newStatus);
}
