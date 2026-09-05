package com.smartlab.service;

import com.smartlab.repo.LabArticleRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LabArticleSlugGeneratorTest {
    @Mock private LabArticleRepository articleRepository;
    @InjectMocks private LabArticleSlugGenerator generator;

    @Test void normalizesVietnameseDiacriticsIncludingDStroke() {
        assertThat(generator.normalize("Bài viết")).isEqualTo("bai-viet");
        assertThat(generator.normalize("Đặc biệt đấy")).isEqualTo("dac-biet-day");
    }

    @Test void generatesDeterministicSecondCandidateOnActiveCollision() {
        when(articleRepository.existsBySlugAndDeletedAtIsNull("bai-viet")).thenReturn(true);
        when(articleRepository.existsBySlugAndDeletedAtIsNull("bai-viet-2")).thenReturn(false);

        assertThat(generator.generateUniqueSlug("Bài viết")).isEqualTo("bai-viet-2");
    }

    @Test void rejectsExplicitBlankSlugNormalization() {
        assertThatThrownBy(() -> generator.normalize("  ---  "))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }
}
