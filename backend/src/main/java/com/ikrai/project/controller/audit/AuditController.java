package com.ikrai.project.controller.audit;

import com.ikrai.project.common.result.Result;
import com.ikrai.project.config.SecurityUsers;
import com.ikrai.project.query.AuditQuery;
import com.ikrai.project.service.audit.AuditService;
import com.ikrai.project.vo.AuditLogVO;
import com.ikrai.project.vo.PageVO;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@RestController
@RequestMapping("/api/audit-logs")
@PreAuthorize("hasRole('ADMIN')")
public class AuditController {

    private final AuditService auditService;

    public AuditController(AuditService auditService) {
        this.auditService = auditService;
    }

    @GetMapping
    public Result<PageVO<AuditLogVO>> list(AuditQuery query) {
        return Result.ok(auditService.list(query, SecurityUsers.current()));
    }

    @GetMapping("/export")
    public void export(AuditQuery query, HttpServletResponse response) throws IOException {
        String filename = "审计日志-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")) + ".xlsx";
        byte[] bytes = auditService.export(query, SecurityUsers.current());
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding("utf-8");
        String encoded = URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20");
        response.setHeader("Content-Disposition", "attachment;filename*=utf-8''" + encoded);
        response.getOutputStream().write(bytes);
    }
}
