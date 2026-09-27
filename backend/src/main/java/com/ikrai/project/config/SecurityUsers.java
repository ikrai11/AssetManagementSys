package com.ikrai.project.config;

import com.ikrai.project.common.AuthUser;
import com.ikrai.project.common.exception.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class SecurityUsers {

    private SecurityUsers() {
    }

    public static AuthUser current() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthUser user)) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "未登录");
        }
        return user;
    }
}
