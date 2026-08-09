package com.smartlab.service;

import com.smartlab.repo.PostRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.text.Normalizer;
import java.util.Locale;

@Component
@RequiredArgsConstructor
public class PostSlugGenerator {
    private static final int MAX_SLUG_LENGTH = 260;
    private static final int MAX_CANDIDATES = 1_000;

    private final PostRepository postRepository;

    public String generateUniqueSlug(String title) {
        String base = normalize(title);
        for (int candidateNumber = 1; candidateNumber <= MAX_CANDIDATES; candidateNumber++) {
            String candidate = candidate(base, candidateNumber);
            if (!postRepository.existsBySlug(candidate)) {
                return candidate;
            }
        }

        throw new ResponseStatusException(HttpStatus.CONFLICT, "Unable to allocate a unique post slug");
    }

    public String candidateFor(String title, int candidateNumber) {
        if (candidateNumber < 1 || candidateNumber > MAX_CANDIDATES) {
            throw new IllegalArgumentException("Slug candidate number is outside the bounded allocation range");
        }
        return candidate(normalize(title), candidateNumber);
    }

    public int maxCandidates() {
        return MAX_CANDIDATES;
    }

    private String normalize(String title) {
        String normalized = Normalizer.normalize(title == null ? "" : title, Normalizer.Form.NFD)
                .replace('đ', 'd')
                .replace('Đ', 'D')
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
        return normalized.isEmpty() ? "post" : truncate(normalized, MAX_SLUG_LENGTH);
    }

    private String candidate(String base, int candidateNumber) {
        String suffix = candidateNumber == 1 ? "" : "-" + candidateNumber;
        return truncate(base, MAX_SLUG_LENGTH - suffix.length()) + suffix;
    }

    private String truncate(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
