package com.ikrai.project.common;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class AuthUser {

    private final Long userId;
    private final String username;
    private final String role;
    private final int tokenVersion;

    public boolean isAdmin() {
        return "ADMIN".equals(role);
    }
}
