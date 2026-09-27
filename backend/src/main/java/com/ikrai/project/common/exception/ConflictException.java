package com.ikrai.project.common.exception;

import org.springframework.http.HttpStatus;

public class ConflictException extends BusinessException {

    public ConflictException() {
        super(HttpStatus.CONFLICT, "状态已变化，请刷新");
    }

    public ConflictException(String message) {
        super(HttpStatus.CONFLICT, message);
    }
}
