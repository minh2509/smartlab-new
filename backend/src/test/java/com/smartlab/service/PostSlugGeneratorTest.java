package com.smartlab.service;

import com.smartlab.repo.PostRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PostSlugGeneratorTest {

    @Mock
    private PostRepository postRepository;

    @Test
    void normalizesBasicUppercaseWhitespaceAndSeparators() {
        when(postRepository.existsBySlug("hello-world")).thenReturn(false);

        assertThat(generator().generateUniqueSlug("  HELLO___World!!!  ")).isEqualTo("hello-world");
    }

    @Test
    void normalizesVietnameseDiacriticsDeterministically() {
        when(postRepository.existsBySlug("tieng-viet-dac-biet")).thenReturn(false);

        assertThat(generator().generateUniqueSlug("Tiếng Việt Đặc Biệt")).isEqualTo("tieng-viet-dac-biet");
    }

    @Test
    void usesPostForAnEmptyNormalizedTitle() {
        when(postRepository.existsBySlug("post")).thenReturn(false);

        assertThat(generator().generateUniqueSlug("---" )).isEqualTo("post");
    }

    @Test
    void incrementsKnownPreexistingSlugCollisions() {
        when(postRepository.existsBySlug("post")).thenReturn(true);
        when(postRepository.existsBySlug("post-2")).thenReturn(true);
        when(postRepository.existsBySlug("post-3")).thenReturn(false);

        assertThat(generator().generateUniqueSlug("post")).isEqualTo("post-3");
    }

    @Test
    void treatsSoftDeletedSlugsAsOccupiedThroughGenericSlugExistence() {
        when(postRepository.existsBySlug("retained-slug")).thenReturn(true);
        when(postRepository.existsBySlug("retained-slug-2")).thenReturn(false);

        assertThat(generator().generateUniqueSlug("Retained Slug")).isEqualTo("retained-slug-2");
        verify(postRepository).existsBySlug("retained-slug");
    }

    @Test
    void limitsBaseAndSuffixedCandidatesToTwoHundredSixtyCharacters() {
        String title = "a".repeat(300);
        String base = "a".repeat(260);
        String suffixed = "a".repeat(258) + "-2";
        when(postRepository.existsBySlug(base)).thenReturn(true);
        when(postRepository.existsBySlug(suffixed)).thenReturn(false);

        String slug = generator().generateUniqueSlug(title);

        assertThat(slug).isEqualTo(suffixed).hasSizeLessThanOrEqualTo(260);
    }

    @Test
    void failsExplicitlyAfterBoundedAllocationAttempts() {
        when(postRepository.existsBySlug(anyString())).thenReturn(true);

        assertThatThrownBy(() -> generator().generateUniqueSlug("post"))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void everyBoundedRetryCandidateRespectsMaximumLength() {
        PostSlugGenerator generator = generator();
        String title = "a".repeat(500);

        for (int candidateNumber = 1; candidateNumber <= generator.maxCandidates(); candidateNumber++) {
            assertThat(generator.candidateFor(title, candidateNumber)).hasSizeLessThanOrEqualTo(260);
        }
    }

    private PostSlugGenerator generator() {
        return new PostSlugGenerator(postRepository);
    }
}
