package com.ikrai.project.manager.user;

import com.ikrai.project.common.exception.BusinessException;
import com.ikrai.project.service.system.SysParamService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class LoginLockManager {

    private static final int DEFAULT_MAX_FAILURES = 5;
    private static final int DEFAULT_LOCK_MINUTES = 15;

    private final SysParamService sysParamService;
    private final Map<String, Attempt> attempts = new ConcurrentHashMap<>();

    public LoginLockManager() {
        this.sysParamService = null;
    }

    @Autowired
    public LoginLockManager(SysParamService sysParamService) {
        this.sysParamService = sysParamService;
    }

    public void assertUnlocked(String username) {
        Attempt attempt = attempts.get(username);
        if (attempt == null || attempt.lockedUntil == null) {
            return;
        }
        if (Instant.now().isBefore(attempt.lockedUntil)) {
            long minutes = Math.max(1, Duration.between(Instant.now(), attempt.lockedUntil).toMinutes());
            throw new BusinessException("账号已锁定，请 " + minutes + " 分钟后再试");
        }
        attempts.remove(username);
    }

    public void recordFailure(String username) {
        Attempt attempt = attempts.computeIfAbsent(username, key -> new Attempt());
        attempt.failures += 1;
        if (attempt.failures >= maxFailures()) {
            attempt.lockedUntil = Instant.now().plus(Duration.ofMinutes(lockMinutes()));
        }
    }

    private int maxFailures() {
        return sysParamService == null ? DEFAULT_MAX_FAILURES : sysParamService.loginMaxFailures();
    }

    private int lockMinutes() {
        return sysParamService == null ? DEFAULT_LOCK_MINUTES : sysParamService.loginLockMinutes();
    }

    public void clear(String username) {
        attempts.remove(username);
    }

    private static class Attempt {
        private int failures;
        private Instant lockedUntil;
    }
}
