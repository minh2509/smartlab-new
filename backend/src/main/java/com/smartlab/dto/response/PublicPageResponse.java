package com.smartlab.dto.response;

import org.springframework.data.domain.Page;

import java.util.List;

public record PublicPageResponse<T>(
        List<T> items,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
    public static <T> PublicPageResponse<T> from(Page<T> page) {
        return new PublicPageResponse<>(
                page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages()
        );
    }
}
