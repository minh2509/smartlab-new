package com.smartlab.service;

import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public final class PasswordPolicy {
    private PasswordPolicy() { }

    public static void validate(String password) {
        if (password == null || password.isBlank() || password.length() < 6
                || password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Mật khẩu phải có ít nhất 6 ký tự và không vượt quá 72 byte UTF-8.");
        }
    }
}
