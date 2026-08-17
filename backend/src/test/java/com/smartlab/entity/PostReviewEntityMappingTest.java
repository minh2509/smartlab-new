package com.smartlab.entity;

import com.smartlab.enums.ReviewDecision;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.time.Instant;
import java.util.Arrays;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PostReviewEntityMappingTest {

    private static final Instant CREATED_AT = Instant.parse("2026-08-10T10:00:00Z");

    @Test
    void mapsTheApprovedPostReviewsTableAndFields() {
        assertThat(PostReviewEntity.class.isAnnotationPresent(Entity.class)).isTrue();
        assertThat(PostReviewEntity.class.getAnnotation(Table.class).name()).isEqualTo("post_reviews");
        assertThat(fieldNames()).containsExactlyInAnyOrder(
                "id",
                "postId",
                "reviewerUserId",
                "decision",
                "reason",
                "createdAt"
        );
    }

    @Test
    void mapsIdentityAndScalarForeignKeyColumns() throws NoSuchFieldException {
        Field id = field("id");

        assertThat(id.isAnnotationPresent(Id.class)).isTrue();
        assertThat(id.isAnnotationPresent(GeneratedValue.class)).isTrue();
        assertThat(id.getAnnotation(GeneratedValue.class).strategy()).isEqualTo(GenerationType.IDENTITY);
        assertThat(id.getType()).isEqualTo(Long.class);
        assertColumn("postId", "post_id", false, 255);
        assertColumn("reviewerUserId", "reviewer_user_id", true, 255);
        assertThat(field("postId").getType()).isEqualTo(Long.class);
        assertThat(field("reviewerUserId").getType()).isEqualTo(Long.class);
    }

    @Test
    void mapsDecisionReasonAndCreationTimestamp() throws NoSuchFieldException {
        Field decision = field("decision");

        assertThat(decision.getType()).isEqualTo(ReviewDecision.class);
        assertThat(decision.isAnnotationPresent(Enumerated.class)).isTrue();
        assertThat(decision.getAnnotation(Enumerated.class).value()).isEqualTo(EnumType.STRING);
        assertColumn("decision", "decision", false, 20);
        assertColumn("reason", "reason", true, 1000);
        assertColumn("createdAt", "created_at", false, 255);
        assertThat(field("createdAt").getType()).isEqualTo(Instant.class);
    }

    @Test
    void usesNoObjectGraphRelationships() {
        assertThat(Arrays.stream(PostReviewEntity.class.getDeclaredFields()))
                .noneMatch(field -> field.isAnnotationPresent(ManyToOne.class)
                        || field.isAnnotationPresent(OneToOne.class)
                        || field.isAnnotationPresent(OneToMany.class));
    }

    @Test
    void hasProtectedJpaConstructorThatAllowsHistoricalNullReviewerHydration() throws Exception {
        Constructor<PostReviewEntity> constructor = PostReviewEntity.class.getDeclaredConstructor();

        assertThat(Modifier.isProtected(constructor.getModifiers())).isTrue();
        assertThat(new PostReviewEntity().getReviewerUserId()).isNull();
    }

    @Test
    void createsValidReviewsForEveryDecision() {
        assertThat(review(ReviewDecision.APPROVED, null).getDecision()).isEqualTo(ReviewDecision.APPROVED);
        assertThat(review(ReviewDecision.REVISION_REQUIRED, "Please clarify the conclusion").getReason())
                .isEqualTo("Please clarify the conclusion");
        assertThat(review(ReviewDecision.REJECTED, "Needs changes").getDecision()).isEqualTo(ReviewDecision.REJECTED);
    }

    @Test
    void preservesReasonWithoutTrimmingOrRewritingIt() {
        String reason = "  Needs changes  ";

        assertThat(review(ReviewDecision.REVISION_REQUIRED, reason).getReason()).isEqualTo(reason);
        assertThat(review(ReviewDecision.REJECTED, reason).getReason()).isEqualTo(reason);
    }

    @ParameterizedTest
    @EnumSource(ReviewDecision.class)
    void acceptsReasonWithExactlyOneThousandCharacters(ReviewDecision decision) {
        assertThat(review(decision, "a".repeat(1_000)).getReason()).hasSize(1_000);
    }

    @ParameterizedTest
    @EnumSource(ReviewDecision.class)
    void rejectsReasonLongerThanOneThousandCharacters(ReviewDecision decision) {
        assertThatThrownBy(() -> review(decision, "a".repeat(1_001)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Review reason must be at most 1000 characters");
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", " \t\n "})
    void rejectsRevisionRequiredAndRejectedDecisionsWithoutANonblankReason(String reason) {
        assertThatThrownBy(() -> review(ReviewDecision.REVISION_REQUIRED, reason))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Revision-required and rejected review reasons must be non-blank");
        assertThatThrownBy(() -> review(ReviewDecision.REJECTED, reason))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Revision-required and rejected review reasons must be non-blank");
    }

    @Test
    void rejectsNullRequiredCreationValues() {
        assertThatThrownBy(() -> PostReviewEntity.create(null, 2L, ReviewDecision.APPROVED, null, CREATED_AT))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("Post ID is required");
        assertThatThrownBy(() -> PostReviewEntity.create(1L, null, ReviewDecision.APPROVED, null, CREATED_AT))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("Reviewer user ID is required");
        assertThatThrownBy(() -> PostReviewEntity.create(1L, 2L, null, null, CREATED_AT))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("Review decision is required");
        assertThatThrownBy(() -> PostReviewEntity.create(1L, 2L, ReviewDecision.APPROVED, null, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("Review creation instant is required");
    }

    @Test
    void rejectsNonPositiveIds() {
        assertThatThrownBy(() -> PostReviewEntity.create(0L, 2L, ReviewDecision.APPROVED, null, CREATED_AT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Post ID must be positive");
        assertThatThrownBy(() -> PostReviewEntity.create(-1L, 2L, ReviewDecision.APPROVED, null, CREATED_AT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Post ID must be positive");
        assertThatThrownBy(() -> PostReviewEntity.create(1L, 0L, ReviewDecision.APPROVED, null, CREATED_AT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Reviewer user ID must be positive");
        assertThatThrownBy(() -> PostReviewEntity.create(1L, -1L, ReviewDecision.APPROVED, null, CREATED_AT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Reviewer user ID must be positive");
    }

    private static PostReviewEntity review(ReviewDecision decision, String reason) {
        return PostReviewEntity.create(1L, 2L, decision, reason, CREATED_AT);
    }

    private static Set<String> fieldNames() {
        return Arrays.stream(PostReviewEntity.class.getDeclaredFields())
                .map(Field::getName)
                .collect(java.util.stream.Collectors.toSet());
    }

    private static Field field(String name) throws NoSuchFieldException {
        return PostReviewEntity.class.getDeclaredField(name);
    }

    private static void assertColumn(String fieldName, String columnName, boolean nullable, int length) throws NoSuchFieldException {
        Column column = field(fieldName).getAnnotation(Column.class);

        assertThat(column.name()).isEqualTo(columnName);
        assertThat(column.nullable()).isEqualTo(nullable);
        assertThat(column.length()).isEqualTo(length);
    }
}
