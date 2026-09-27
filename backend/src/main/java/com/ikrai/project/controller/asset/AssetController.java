package com.ikrai.project.controller.asset;

import com.ikrai.project.common.result.Result;
import com.ikrai.project.config.SecurityUsers;
import com.ikrai.project.dto.AssetSaveDTO;
import com.ikrai.project.query.AssetQuery;
import com.ikrai.project.service.asset.AssetExcelService;
import com.ikrai.project.service.asset.AssetService;
import com.ikrai.project.vo.AssetDetailVO;
import com.ikrai.project.vo.AssetImportVO;
import com.ikrai.project.vo.AssetVO;
import com.ikrai.project.vo.PageVO;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@RestController
@RequestMapping("/api/assets")
public class AssetController {

    private final AssetService assetService;
    private final AssetExcelService assetExcelService;

    public AssetController(AssetService assetService, AssetExcelService assetExcelService) {
        this.assetService = assetService;
        this.assetExcelService = assetExcelService;
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

    @GetMapping("/import-template")
    @PreAuthorize("hasRole('ADMIN')")
    public void importTemplate(HttpServletResponse response) throws IOException {
        writeExcel(response, "设备导入模板.xlsx", assetExcelService.template());
    }

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    public Result<AssetImportVO> importExcel(@RequestParam("file") MultipartFile file) {
        return Result.ok(assetExcelService.importAssets(SecurityUsers.current(), file));
    }

    @GetMapping("/import-failures")
    @PreAuthorize("hasRole('ADMIN')")
    public void importFailures(HttpServletResponse response) throws IOException {
        writeExcel(response, "导入失败明细.xlsx", assetExcelService.lastFailFile(SecurityUsers.current()));
    }

    @GetMapping("/export")
    @PreAuthorize("hasRole('ADMIN')")
    public void export(AssetQuery query, HttpServletResponse response) throws IOException {
        String filename = "设备台账-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")) + ".xlsx";
        writeExcel(response, filename, assetExcelService.exportAssets(query, SecurityUsers.current()));
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

    private void writeExcel(HttpServletResponse response, String filename, byte[] bytes) throws IOException {
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding("utf-8");
        String encoded = URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20");
        response.setHeader("Content-Disposition", "attachment;filename*=utf-8''" + encoded);
        response.getOutputStream().write(bytes);
    }
}
