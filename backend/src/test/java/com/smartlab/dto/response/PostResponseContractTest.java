package com.smartlab.dto.response;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class PostResponseContractTest {

    @Test
    void postSummaryExposesExactlyTheApprovedFields() {
        assertThat(fieldNames(PostSummaryResponse.class)).containsExactlyInAnyOrder(
                "id",
                "title",
                "slug",
                "excerpt",
                "visibility",
                "projectId",
                "status",
                "category",
                "author",
                "publishedAt",
                "createdAt",
                "updatedAt"
        );
    }

    @Test
    void postSummaryDoesNotExposeContentJson() {
        assertThat(fieldNames(PostSummaryResponse.class)).doesNotContain("contentJson");
    }

    @Test
    void postDetailExposesExactlyTheApprovedFields() {
        assertThat(fieldNames(PostDetailResponse.class)).containsExactlyInAnyOrder(
                "id",
                "title",
                "slug",
                "excerpt",
                "contentJson",
                "visibility",
                "projectId",
                "status",
                "category",
                "author",
                "reviewFeedback",
                "publishedAt",
                "createdAt",
                "updatedAt"
        );
        assertThat(PostDetailResponse.class.getDeclaredFields())
                .filteredOn(field -> field.getName().equals("contentJson"))
                .singleElement()
                .extracting(field -> field.getType())
                .isEqualTo(Map.class);
        assertThat(PostDetailResponse.class.getDeclaredFields())
                .filteredOn(field -> field.getName().equals("reviewFeedback"))
                .singleElement()
                .extracting(field -> field.getType())
                .isEqualTo(PostReviewFeedbackResponse.class);
    }

    @Test
    void postResponsesDoNotExposeForbiddenFields() {
        Set<String> forbiddenFields = Set.of(
                "authorUserId",
                "contentHtml",
                "coverFileId",
                "deletedAt"
        );

        assertThat(fieldNames(PostSummaryResponse.class)).doesNotContainAnyElementsOf(forbiddenFields);
        assertThat(fieldNames(PostDetailResponse.class)).doesNotContainAnyElementsOf(forbiddenFields);
    }

    @Test
    void categoryResponseExposesOnlyIdCodeAndName() {
        assertThat(fieldNames(PostCategoryResponse.class)).containsExactlyInAnyOrder("id", "code", "name");
    }

    @Test
    void authorResponseExposesOnlyPublicUserIdAndName() {
        assertThat(fieldNames(PostAuthorResponse.class)).containsExactlyInAnyOrder("userId", "name");
    }

    @Test
    void postResponseTimestampsUseInstant() {
        assertThat(PostSummaryResponse.class.getDeclaredFields())
                .filteredOn(field -> Set.of("publishedAt", "createdAt", "updatedAt").contains(field.getName()))
                .allSatisfy(field -> assertThat(field.getType()).isEqualTo(Instant.class));
        assertThat(PostDetailResponse.class.getDeclaredFields())
                .filteredOn(field -> Set.of("publishedAt", "createdAt", "updatedAt").contains(field.getName()))
                .allSatisfy(field -> assertThat(field.getType()).isEqualTo(Instant.class));
    }

    private static Set<String> fieldNames(Class<?> type) {
        return Arrays.stream(type.getDeclaredFields())
                .map(field -> field.getName())
                .collect(java.util.stream.Collectors.toSet());
    }
}
