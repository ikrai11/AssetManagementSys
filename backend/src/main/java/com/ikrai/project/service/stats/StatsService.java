package com.ikrai.project.service.stats;

import com.ikrai.project.common.AuthUser;
import com.ikrai.project.vo.StatsNameCountVO;
import com.ikrai.project.vo.StatsOverviewVO;
import com.ikrai.project.vo.StatsTrendVO;

import java.util.List;

public interface StatsService {

    StatsOverviewVO overview(AuthUser viewer);

    List<StatsNameCountVO> byCategory(AuthUser viewer);

    List<StatsTrendVO> borrowTrend(AuthUser viewer);
}
