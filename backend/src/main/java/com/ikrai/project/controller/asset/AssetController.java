package com.ikrai.project.controller.asset;

import com.ikrai.project.common.result.Result;
import com.ikrai.project.config.SecurityUsers;
import com.ikrai.project.dto.AssetSaveDTO;
import com.ikrai.project.query.AssetQuery;
import com.ikrai.project.service.asset.AssetService;
import com.ikrai.project.vo.AssetDetailVO;
import com.ikrai.project.vo.AssetVO;
import com.ikrai.project.vo.PageVO;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/assets")
public class AssetController {

    private final AssetService assetService;

    public AssetController(AssetService assetService) {
        this.assetService = assetService;
    }

    @GetMapping
    public Result<PageVO<AssetVO>> list(AssetQuery query) {
        return Result.ok(assetService.list(query, SecurityUsers.current()));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public Result<AssetVO> save(@Valid @RequestBody AssetSaveDTO dto) {
        return Result.ok(assetService.save(dto));
    }

    @GetMapping("/{id}")
    public Result<AssetDetailVO> get(@PathVariable Long id) {
        return Result.ok(assetService.get(id, SecurityUsers.current()));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<AssetVO> update(@PathVariable Long id, @Valid @RequestBody AssetSaveDTO dto) {
        return Result.ok(assetService.update(id, dto));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> remove(@PathVariable Long id) {
        assetService.remove(id);
        return Result.ok(null);
    }
}
