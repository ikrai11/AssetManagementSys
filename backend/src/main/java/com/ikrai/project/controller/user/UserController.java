package com.ikrai.project.controller.user;

import com.ikrai.project.common.result.Result;
import com.ikrai.project.config.SecurityUsers;
import com.ikrai.project.dto.ChangePasswordDTO;
import com.ikrai.project.dto.LoginDTO;
import com.ikrai.project.service.user.AuthService;
import com.ikrai.project.vo.LoginVO;
import com.ikrai.project.vo.UserVO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class UserController {

    private final AuthService authService;

    public UserController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public Result<LoginVO> login(@Valid @RequestBody LoginDTO dto) {
        return Result.ok(authService.login(dto));
    }

    @PostMapping("/logout")
    public Result<Void> logout() {
        return Result.ok(null);
    }

    @GetMapping("/me")
    public Result<UserVO> me() {
        return Result.ok(authService.getMe(SecurityUsers.current()));
    }

    @PutMapping("/password")
    public Result<Void> password(@Valid @RequestBody ChangePasswordDTO dto) {
        authService.updatePassword(SecurityUsers.current(), dto);
        return Result.ok(null);
    }
}
