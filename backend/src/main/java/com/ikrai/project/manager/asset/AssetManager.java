package com.ikrai.project.manager.asset;

import com.ikrai.project.dataobject.AssetDO;

import java.time.LocalDate;

public interface AssetManager {

    AssetDO getById(Long id);

    void occupyOnSubmit(AssetDO asset, Long borrowId);

    void release(AssetDO asset);

    void issue(AssetDO asset, Long holderUserId, LocalDate borrowStartDate, LocalDate expectedReturnDate);

    void confirmReturn(AssetDO asset);

    void updateExpectedReturn(AssetDO asset, LocalDate expectedReturnDate);

    void changeStatus(AssetDO asset, String fromStatus, String toStatus);

    void relocate(AssetDO asset, Long deptId, Long locationId);
}
