package com.ikrai.project.service.asset;

import com.ikrai.project.common.AuthUser;
import com.ikrai.project.dto.AssetRepairFinishDTO;
import com.ikrai.project.dto.AssetRepairStartDTO;
import com.ikrai.project.dto.AssetScrapDTO;
import com.ikrai.project.dto.AssetTransferDTO;

public interface AssetLifecycleService {

    void transfer(AuthUser operator, Long assetId, AssetTransferDTO dto);

    void startRepair(AuthUser operator, Long assetId, AssetRepairStartDTO dto);

    void finishRepair(AuthUser operator, Long assetId, AssetRepairFinishDTO dto);

    void scrap(AuthUser operator, Long assetId, AssetScrapDTO dto);
}
