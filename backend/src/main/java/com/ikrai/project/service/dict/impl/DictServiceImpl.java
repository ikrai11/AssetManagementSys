package com.ikrai.project.service.dict.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.ikrai.project.dao.AssetCategoryDao;
import com.ikrai.project.dao.SysDeptDao;
import com.ikrai.project.dao.SysLocationDao;
import com.ikrai.project.dataobject.AssetCategoryDO;
import com.ikrai.project.dataobject.SysDeptDO;
import com.ikrai.project.dataobject.SysLocationDO;
import com.ikrai.project.service.dict.DictService;
import com.ikrai.project.vo.DictVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class DictServiceImpl implements DictService {

    private final AssetCategoryDao assetCategoryDao;
    private final SysDeptDao sysDeptDao;
    private final SysLocationDao sysLocationDao;

    @Override
    public List<DictVO> listCategories() {
        return assetCategoryDao.selectList(Wrappers.<AssetCategoryDO>lambdaQuery()
                        .eq(AssetCategoryDO::getEnabled, 1))
                .stream()
                .map(item -> new DictVO(item.getId(), item.getName()))
                .toList();
    }

    @Override
    public List<DictVO> listDepts() {
        return sysDeptDao.selectList(Wrappers.<SysDeptDO>lambdaQuery()
                        .eq(SysDeptDO::getEnabled, 1))
                .stream()
                .map(item -> new DictVO(item.getId(), item.getName()))
                .toList();
    }

    @Override
    public List<DictVO> listLocations() {
        return sysLocationDao.selectList(Wrappers.<SysLocationDO>lambdaQuery()
                        .eq(SysLocationDO::getEnabled, 1))
                .stream()
                .map(item -> new DictVO(item.getId(), item.getName()))
                .toList();
    }
}
