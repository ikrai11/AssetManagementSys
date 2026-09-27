package com.ikrai.project.service.dict;

import com.ikrai.project.vo.DictVO;

import java.util.List;

public interface DictService {

    List<DictVO> listCategories();

    List<DictVO> listDepts();

    List<DictVO> listLocations();
}
