package com.ikrai.project.manager.user;

import com.ikrai.project.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoginLockManagerTest {

    @Test
    void lockAfterFiveFailuresShowsRemainingMinutes() {
        LoginLockManager manager = new LoginLockManager();
        String username = "lock_user";
        for (int i = 0; i < 5; i++) {
            manager.recordFailure(username);
        }

        BusinessException ex = assertThrows(BusinessException.class, () -> manager.assertUnlocked(username));
        assertTrue(ex.getMessage().contains("锁定"));
        assertTrue(ex.getMessage().contains("分钟"));
    }

    @Test
    void successClearsFailures() {
        LoginLockManager manager = new LoginLockManager();
        for (int i = 0; i < 4; i++) {
            manager.recordFailure("retry_user");
        }
        manager.clear("retry_user");
        assertDoesNotThrow(() -> manager.assertUnlocked("retry_user"));
    }
}
