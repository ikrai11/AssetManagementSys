package com.ikrai.project.controller.system;

import com.ikrai.project.common.result.Result;
import com.ikrai.project.config.SecurityUsers;
import com.ikrai.project.dto.SysParamUpdateDTO;
import com.ikrai.project.service.system.SysParamService;
import com.ikrai.project.vo.SysParamVO;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/system-params")
public class SysParamController {

    private final SysParamService sysParamService;

    public SysParamController(SysParamService sysParamService) {
        this.sysParamService = sysParamService;
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public Result<SysParamVO> get() {
        return Result.ok(sysParamService.get());
    }

    @PutMapping
    @PreAuthorize("hasRole('ADMIN')")
    public Result<SysParamVO> update(@Valid @RequestBody SysParamUpdateDTO dto) {
        return Result.ok(sysParamService.update(SecurityUsers.current(), dto));
    }
}
