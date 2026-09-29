package com.ikrai.project.service.stocktake;

import com.ikrai.project.common.AuthUser;
import com.ikrai.project.dto.StocktakeCreateDTO;
import com.ikrai.project.dto.StocktakeMarkDTO;
import com.ikrai.project.vo.StocktakeVO;

import java.util.List;

public interface StocktakeService {

    StocktakeVO create(AuthUser operator, StocktakeCreateDTO dto);

    List<StocktakeVO> list();

    StocktakeVO get(Long id);

    StocktakeVO mark(AuthUser operator, Long stocktakeId, Long itemId, StocktakeMarkDTO dto);

    StocktakeVO finish(AuthUser operator, Long id);
}
