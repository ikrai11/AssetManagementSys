package com.ikrai.project.service.asset;

import com.ikrai.project.common.AuthUser;
import com.ikrai.project.dto.AssetSaveDTO;
import com.ikrai.project.query.AssetQuery;
import com.ikrai.project.vo.AssetDetailVO;
import com.ikrai.project.vo.AssetVO;
import com.ikrai.project.vo.PageVO;

public interface AssetService {

    AssetVO save(AssetSaveDTO dto);

    AssetVO save(AuthUser operator, AssetSaveDTO dto);

    AssetVO update(Long id, AssetSaveDTO dto);

    AssetVO update(AuthUser operator, Long id, AssetSaveDTO dto);

    void remove(Long id);

    void remove(AuthUser operator, Long id);

    AssetDetailVO get(Long id, AuthUser viewer);

    PageVO<AssetVO> list(AssetQuery query, AuthUser viewer);

    long count(AssetQuery query, AuthUser viewer);

    java.util.List<AssetVO> listAll(AssetQuery query, AuthUser viewer);
}
