package com.ikrai.project.controller.org;

import com.ikrai.project.common.result.Result;
import com.ikrai.project.config.SecurityUsers;
import com.ikrai.project.dto.OrgSaveDTO;
import com.ikrai.project.service.org.OrgService;
import com.ikrai.project.vo.OrgNodeVO;
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

import java.util.List;

@RestController
@RequestMapping("/api/org")
@PreAuthorize("hasRole('ADMIN')")
public class OrgController {

    private final OrgService orgService;

    public OrgController(OrgService orgService) {
        this.orgService = orgService;
    }

    @GetMapping("/depts")
    public Result<List<OrgNodeVO>> depts() {
        return Result.ok(orgService.listDepts(SecurityUsers.current()));
    }

    @PostMapping("/depts")
    public Result<OrgNodeVO> createDept(@Valid @RequestBody OrgSaveDTO dto) {
        return Result.ok(orgService.saveDept(SecurityUsers.current(), dto));
    }

    @PutMapping("/depts/{id}")
    public Result<OrgNodeVO> updateDept(@PathVariable Long id, @Valid @RequestBody OrgSaveDTO dto) {
        return Result.ok(orgService.updateDept(SecurityUsers.current(), id, dto));
    }

    @DeleteMapping("/depts/{id}")
    public Result<Void> removeDept(@PathVariable Long id) {
        orgService.removeDept(SecurityUsers.current(), id);
        return Result.ok(null);
    }

    @GetMapping("/locations")
    public Result<List<OrgNodeVO>> locations() {
        return Result.ok(orgService.listLocations(SecurityUsers.current()));
    }

    @PostMapping("/locations")
    public Result<OrgNodeVO> createLocation(@Valid @RequestBody OrgSaveDTO dto) {
        return Result.ok(orgService.saveLocation(SecurityUsers.current(), dto));
    }

    @PutMapping("/locations/{id}")
    public Result<OrgNodeVO> updateLocation(@PathVariable Long id, @Valid @RequestBody OrgSaveDTO dto) {
        return Result.ok(orgService.updateLocation(SecurityUsers.current(), id, dto));
    }

    @DeleteMapping("/locations/{id}")
    public Result<Void> removeLocation(@PathVariable Long id) {
        orgService.removeLocation(SecurityUsers.current(), id);
        return Result.ok(null);
    }
}
