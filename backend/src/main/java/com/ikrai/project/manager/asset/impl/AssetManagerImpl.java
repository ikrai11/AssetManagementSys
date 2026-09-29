package com.ikrai.project.manager.asset.impl;

import com.ikrai.project.common.enums.AssetStatus;
import com.ikrai.project.common.exception.ConflictException;
import com.ikrai.project.common.exception.NotFoundException;
import com.ikrai.project.dao.AssetDao;
import com.ikrai.project.dataobject.AssetDO;
import com.ikrai.project.manager.asset.AssetManager;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
public class AssetManagerImpl implements AssetManager {

    private final AssetDao assetDao;

    public AssetManagerImpl(AssetDao assetDao) {
        this.assetDao = assetDao;
    }

    @Override
    public AssetDO getById(Long id) {
        AssetDO asset = assetDao.selectById(id);
        if (asset == null) {
            throw new NotFoundException("设备不存在");
        }
        return asset;
    }

    @Override
    public void occupyOnSubmit(AssetDO asset, Long borrowId) {
        int rows = assetDao.casOccupy(
                asset.getId(),
                asset.getVersion(),
                AssetStatus.IN_STOCK.name(),
                AssetStatus.PENDING.name(),
                borrowId
        );
        if (rows == 0) {
            throw new ConflictException();
        }
    }

    @Override
    public void release(AssetDO asset) {
        int rows = assetDao.casRelease(
                asset.getId(),
                asset.getVersion(),
                AssetStatus.PENDING.name(),
                AssetStatus.IN_STOCK.name()
        );
        if (rows == 0) {
            throw new ConflictException();
        }
    }

    @Override
    public void issue(AssetDO asset, Long holderUserId, LocalDate borrowStartDate, LocalDate expectedReturnDate) {
        int rows = assetDao.casIssue(
                asset.getId(),
                asset.getVersion(),
                AssetStatus.PENDING.name(),
                AssetStatus.BORROWED.name(),
                holderUserId,
                borrowStartDate,
                expectedReturnDate
        );
        if (rows == 0) {
            throw new ConflictException();
        }
    }

    @Override
    public void confirmReturn(AssetDO asset) {
        int rows = assetDao.casReturn(
                asset.getId(),
                asset.getVersion(),
                AssetStatus.BORROWED.name(),
                AssetStatus.IN_STOCK.name()
        );
        if (rows == 0) {
            throw new ConflictException();
        }
    }

    @Override
    public void updateExpectedReturn(AssetDO asset, LocalDate expectedReturnDate) {
        int rows = assetDao.casUpdateExpectedReturn(
                asset.getId(),
                asset.getVersion(),
                AssetStatus.BORROWED.name(),
                expectedReturnDate
        );
        if (rows == 0) {
            throw new ConflictException();
        }
    }

    @Override
    public void changeStatus(AssetDO asset, String fromStatus, String toStatus) {
        int rows = assetDao.casStatus(asset.getId(), asset.getVersion(), fromStatus, toStatus);
        if (rows == 0) {
            throw new ConflictException();
        }
    }

    @Override
    public void relocate(AssetDO asset, Long deptId, Long locationId) {
        int rows = assetDao.casRelocate(asset.getId(), asset.getVersion(), deptId, locationId);
        if (rows == 0) {
            throw new ConflictException();
        }
    }
}
