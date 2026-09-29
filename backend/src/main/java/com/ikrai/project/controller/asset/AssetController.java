package com.ikrai.project.controller.asset;

import com.ikrai.project.common.result.Result;
import com.ikrai.project.config.SecurityUsers;
import com.ikrai.project.dto.AssetRepairFinishDTO;
import com.ikrai.project.dto.AssetRepairStartDTO;
import com.ikrai.project.dto.AssetSaveDTO;
import com.ikrai.project.dto.AssetScrapDTO;
import com.ikrai.project.dto.AssetTransferDTO;
import com.ikrai.project.query.AssetQuery;
import com.ikrai.project.service.asset.AssetExcelService;
import com.ikrai.project.service.asset.AssetFileService;
import com.ikrai.project.service.asset.AssetLifecycleService;
import com.ikrai.project.service.asset.AssetService;
import com.ikrai.project.vo.AssetDetailVO;
import com.ikrai.project.vo.AssetFileContent;
import com.ikrai.project.vo.AssetFileVO;
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
import java.util.List;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@RestController
@RequestMapping("/api/assets")
public class AssetController {

    private final AssetService assetService;
    private final AssetExcelService assetExcelService;
    private final AssetLifecycleService assetLifecycleService;
    private final AssetFileService assetFileService;

    public AssetController(AssetService assetService,
                           AssetExcelService assetExcelService,
                           AssetLifecycleService assetLifecycleService,
                           AssetFileService assetFileService) {
        this.assetService = assetService;
        this.assetExcelService = assetExcelService;
        this.assetLifecycleService = assetLifecycleService;
        this.assetFileService = assetFileService;
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

    @GetMapping("/{id}/files")
    public Result<List<AssetFileVO>> listFiles(@PathVariable Long id) {
        return Result.ok(assetFileService.list(SecurityUsers.current(), id));
    }

    @PostMapping(value = "/{id}/files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    public Result<AssetFileVO> uploadFile(@PathVariable Long id,
                                          @RequestParam String kind,
                                          @RequestParam("file") MultipartFile file) {
        return Result.ok(assetFileService.upload(SecurityUsers.current(), id, kind, file));
    }

    @GetMapping("/{id}/files/{fileId}")
    public void downloadFile(@PathVariable Long id, @PathVariable Long fileId, HttpServletResponse response) throws IOException {
        AssetFileContent content = assetFileService.download(SecurityUsers.current(), id, fileId);
        String encoded = URLEncoder.encode(content.getOriginalName(), StandardCharsets.UTF_8).replace("+", "%20");
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("Content-Security-Policy", "default-src 'none'");
        response.setHeader("Content-Disposition", "attachment;filename*=utf-8''" + encoded);
        response.setContentType(content.getContentType());
        response.getOutputStream().write(content.getBytes());
    }

    @DeleteMapping("/{id}/files/{fileId}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> deleteFile(@PathVariable Long id, @PathVariable Long fileId) {
        assetFileService.delete(SecurityUsers.current(), id, fileId);
        return Result.ok(null);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> remove(@PathVariable Long id) {
        assetService.remove(id);
        return Result.ok(null);
    }

    @PostMapping("/{id}/transfer")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> transfer(@PathVariable Long id, @Valid @RequestBody AssetTransferDTO dto) {
        assetLifecycleService.transfer(SecurityUsers.current(), id, dto);
        return Result.ok(null);
    }

    @PostMapping("/{id}/repair")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> startRepair(@PathVariable Long id, @Valid @RequestBody AssetRepairStartDTO dto) {
        assetLifecycleService.startRepair(SecurityUsers.current(), id, dto);
        return Result.ok(null);
    }

    @PostMapping("/{id}/repair/finish")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> finishRepair(@PathVariable Long id, @Valid @RequestBody AssetRepairFinishDTO dto) {
        assetLifecycleService.finishRepair(SecurityUsers.current(), id, dto);
        return Result.ok(null);
    }

    @PostMapping("/{id}/scrap")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> scrap(@PathVariable Long id, @Valid @RequestBody AssetScrapDTO dto) {
        assetLifecycleService.scrap(SecurityUsers.current(), id, dto);
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
