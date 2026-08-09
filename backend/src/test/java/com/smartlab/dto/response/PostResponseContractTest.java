package com.smartlab.dto.response;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Arrays;
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
                "status",
                "category",
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
    void postDetailExposesSummaryFieldsPlusContentJson() {
        assertThat(fieldNames(PostDetailResponse.class)).containsExactlyInAnyOrder(
                "id",
                "title",
                "slug",
                "excerpt",
                "contentJson",
                "visibility",
                "status",
                "category",
                "publishedAt",
                "createdAt",
                "updatedAt"
        );
        assertThat(PostDetailResponse.class.getDeclaredFields())
                .filteredOn(field -> field.getName().equals("contentJson"))
                .singleElement()
                .extracting(field -> field.getType())
                .isEqualTo(JsonNode.class);
    }

    @Test
    void postResponsesDoNotExposeForbiddenFields() {
        Set<String> forbiddenFields = Set.of(
                "authorUserId",
                "contentHtml",
                "projectId",
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
