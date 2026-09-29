package com.ikrai.project.common.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum AssetFileKind {
    PHOTO("照片"),
    INVOICE("票据");

    private final String label;

    public static AssetFileKind parse(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return AssetFileKind.valueOf(value.trim());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
