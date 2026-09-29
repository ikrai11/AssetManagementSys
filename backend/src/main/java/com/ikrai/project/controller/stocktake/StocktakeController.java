package com.ikrai.project.controller.stocktake;

import com.ikrai.project.common.result.Result;
import com.ikrai.project.config.SecurityUsers;
import com.ikrai.project.dto.StocktakeCreateDTO;
import com.ikrai.project.dto.StocktakeMarkDTO;
import com.ikrai.project.service.stocktake.StocktakeService;
import com.ikrai.project.vo.StocktakeVO;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/stocktakes")
@PreAuthorize("hasRole('ADMIN')")
public class StocktakeController {

    private final StocktakeService stocktakeService;

    public StocktakeController(StocktakeService stocktakeService) {
        this.stocktakeService = stocktakeService;
    }

    @GetMapping
    public Result<List<StocktakeVO>> list() {
        return Result.ok(stocktakeService.list());
    }

    @PostMapping
    public Result<StocktakeVO> create(@RequestBody StocktakeCreateDTO dto) {
        return Result.ok(stocktakeService.create(SecurityUsers.current(), dto));
    }

    @GetMapping("/{id}")
    public Result<StocktakeVO> get(@PathVariable Long id) {
        return Result.ok(stocktakeService.get(id));
    }

    @PostMapping("/{id}/items/{itemId}/mark")
    public Result<StocktakeVO> mark(@PathVariable Long id,
                                    @PathVariable Long itemId,
                                    @RequestBody StocktakeMarkDTO dto) {
        return Result.ok(stocktakeService.mark(SecurityUsers.current(), id, itemId, dto));
    }

    @PostMapping("/{id}/finish")
    public Result<StocktakeVO> finish(@PathVariable Long id) {
        return Result.ok(stocktakeService.finish(SecurityUsers.current(), id));
    }
}
