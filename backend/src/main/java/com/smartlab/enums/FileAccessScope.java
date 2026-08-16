package com.smartlab.enums;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Locale;

public enum FileAccessScope {
    PUBLIC,
    LAB,
    PROJECT,
    PRIVATE;

    public static FileAccessScope from(String value, FileAccessScope defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return FileAccessScope.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Access scope must be one of PUBLIC, LAB, PROJECT, or PRIVATE"
            );
        }
    }
}
