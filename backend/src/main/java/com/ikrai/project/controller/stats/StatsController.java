package com.ikrai.project.controller.stats;

import com.ikrai.project.common.result.Result;
import com.ikrai.project.config.SecurityUsers;
import com.ikrai.project.service.stats.StatsService;
import com.ikrai.project.vo.StatsNameCountVO;
import com.ikrai.project.vo.StatsOverviewVO;
import com.ikrai.project.vo.StatsTrendVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/stats")
public class StatsController {

    private final StatsService statsService;

    public StatsController(StatsService statsService) {
        this.statsService = statsService;
    }

    @GetMapping("/overview")
    public Result<StatsOverviewVO> overview() {
        return Result.ok(statsService.overview(SecurityUsers.current()));
    }

    @GetMapping("/by-category")
    public Result<List<StatsNameCountVO>> byCategory() {
        return Result.ok(statsService.byCategory(SecurityUsers.current()));
    }

    @GetMapping("/borrow-trend")
    public Result<List<StatsTrendVO>> borrowTrend() {
        return Result.ok(statsService.borrowTrend(SecurityUsers.current()));
    }
}
