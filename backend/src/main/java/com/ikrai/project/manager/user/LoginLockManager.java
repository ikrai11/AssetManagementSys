package com.ikrai.project.manager.user;

import com.ikrai.project.common.exception.BusinessException;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class LoginLockManager {

    private static final int MAX_FAILURES = 5;
    private static final Duration LOCK_DURATION = Duration.ofMinutes(15);

    private final Map<String, Attempt> attempts = new ConcurrentHashMap<>();

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
        if (attempt.failures >= MAX_FAILURES) {
            attempt.lockedUntil = Instant.now().plus(LOCK_DURATION);
        }
    }

    public void clear(String username) {
        attempts.remove(username);
    }

    private static class Attempt {
        private int failures;
        private Instant lockedUntil;
    }
}
