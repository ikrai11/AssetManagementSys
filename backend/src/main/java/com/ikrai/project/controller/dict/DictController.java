package com.ikrai.project.controller.dict;

import com.ikrai.project.common.result.Result;
import com.ikrai.project.service.dict.DictService;
import com.ikrai.project.vo.DictVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class DictController {

    private final DictService dictService;

    @GetMapping("/api/categories")
    public Result<List<DictVO>> categories() {
        return Result.ok(dictService.listCategories());
    }

    @GetMapping("/api/depts")
    public Result<List<DictVO>> depts() {
        return Result.ok(dictService.listDepts());
    }

    @GetMapping("/api/locations")
    public Result<List<DictVO>> locations() {
        return Result.ok(dictService.listLocations());
    }
}
