package com.smartlab.service;

import com.smartlab.repo.LabArticleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.text.Normalizer;
import java.util.Locale;

@Component
@RequiredArgsConstructor
public class LabArticleSlugGenerator {
    private static final int MAX_SLUG_LENGTH = 500;
    private static final int MAX_CANDIDATES = 1_000;
    private final LabArticleRepository articleRepository;

    public String generateUniqueSlug(String value) {
        String base = normalize(value);
        for (int number = 1; number <= MAX_CANDIDATES; number++) {
            String candidate = candidate(base, number);
            if (!articleRepository.existsBySlugAndDeletedAtIsNull(candidate)) return candidate;
        }
        throw new ResponseStatusException(HttpStatus.CONFLICT, "Unable to allocate a unique article slug");
    }

    public String normalize(String value) {
        String normalized = Normalizer.normalize(value == null ? "" : value.trim(), Normalizer.Form.NFD)
                .replace('đ', 'd').replace('Đ', 'D').replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
        if (normalized.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Slug must not be blank");
        return truncate(normalized, MAX_SLUG_LENGTH);
    }

    private String candidate(String base, int number) {
        String suffix = number == 1 ? "" : "-" + number;
        return truncate(base, MAX_SLUG_LENGTH - suffix.length()) + suffix;
    }

    private String truncate(String value, int maxLength) { return value.length() <= maxLength ? value : value.substring(0, maxLength); }
}
