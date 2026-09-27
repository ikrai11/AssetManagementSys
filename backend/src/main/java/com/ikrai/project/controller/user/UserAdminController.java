package com.ikrai.project.controller.user;

import com.ikrai.project.common.result.Result;
import com.ikrai.project.config.SecurityUsers;
import com.ikrai.project.dto.ResetPasswordDTO;
import com.ikrai.project.dto.UserSaveDTO;
import com.ikrai.project.query.UserQuery;
import com.ikrai.project.service.user.UserService;
import com.ikrai.project.vo.PageVO;
import com.ikrai.project.vo.UserVO;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
@PreAuthorize("hasRole('ADMIN')")
public class UserAdminController {

    private final UserService userService;

    public UserAdminController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public Result<PageVO<UserVO>> list(UserQuery query) {
        return Result.ok(userService.list(query));
    }

    @PostMapping
    public Result<UserVO> create(@Valid @RequestBody UserSaveDTO dto) {
        return Result.ok(userService.create(dto));
    }

    @PutMapping("/{id}")
    public Result<UserVO> update(@PathVariable Long id, @Valid @RequestBody UserSaveDTO dto) {
        return Result.ok(userService.update(SecurityUsers.current(), id, dto));
    }

    @PostMapping("/{id}/reset-password")
    public Result<Void> resetPassword(@PathVariable Long id, @Valid @RequestBody ResetPasswordDTO dto) {
        userService.resetPassword(SecurityUsers.current(), id, dto);
        return Result.ok(null);
    }
}
