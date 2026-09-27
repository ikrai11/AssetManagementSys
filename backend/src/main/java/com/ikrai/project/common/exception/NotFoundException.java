package com.ikrai.project.common.exception;

import org.springframework.http.HttpStatus;

public class NotFoundException extends BusinessException {

    public NotFoundException() {
        super(HttpStatus.NOT_FOUND, "资源不存在");
    }

    public NotFoundException(String message) {
        super(HttpStatus.NOT_FOUND, message);
    }
}
