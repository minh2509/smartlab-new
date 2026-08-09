package com.smartlab.entity;

import com.smartlab.enums.PostStatus;
import com.smartlab.enums.PostVisibility;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.time.Instant;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class PostEntityMappingTest {

    @Test
    void mapsTheApprovedPostsTableAndFields() {
        assertThat(PostEntity.class.isAnnotationPresent(Entity.class)).isTrue();
        assertThat(PostEntity.class.getAnnotation(Table.class).name()).isEqualTo("posts");
        assertThat(fieldNames()).containsExactlyInAnyOrder(
                "id",
                "authorUserId",
                "projectId",
                "categoryId",
                "title",
                "slug",
                "excerpt",
                "contentJson",
                "contentHtml",
                "coverFileId",
                "visibility",
                "status",
                "publishedAt",
                "createdAt",
                "updatedAt",
                "deletedAt"
        );
    }

    @Test
    void mapsIdentityId() throws NoSuchFieldException {
        Field id = field("id");

        assertThat(id.isAnnotationPresent(Id.class)).isTrue();
        assertThat(id.isAnnotationPresent(GeneratedValue.class)).isTrue();
        assertThat(id.getAnnotation(GeneratedValue.class).strategy()).isEqualTo(GenerationType.IDENTITY);
        assertThat(id.getType()).isEqualTo(Long.class);
    }

    @Test
    void mapsTitleSlugAndExcerptConstraints() throws NoSuchFieldException {
        assertColumn("title", "title", false, false, 250);
        assertColumn("slug", "slug", false, true, 260);
        assertColumn("excerpt", "excerpt", true, false, 500);
    }

    @Test
    void mapsJavaNeutralObjectMapWithHibernateNativeJsonType() throws NoSuchFieldException {
        Field contentJson = field("contentJson");

        assertThat(contentJson.getType()).isEqualTo(Map.class);
        assertThat(contentJson.getAnnotation(Column.class).name()).isEqualTo("content_json");
        assertThat(contentJson.getAnnotation(Column.class).nullable()).isFalse();
        assertThat(contentJson.isAnnotationPresent(JdbcTypeCode.class)).isTrue();
        assertThat(contentJson.getAnnotation(JdbcTypeCode.class).value()).isEqualTo(SqlTypes.JSON);
    }

    @Test
    void mapsVisibilityAndStatusAsStringEnums() throws NoSuchFieldException {
        assertStringEnum("visibility", PostVisibility.class);
        assertStringEnum("status", PostStatus.class);
    }

    @Test
    void mapsInstantTimestampsAndNullableDeletionTime() throws NoSuchFieldException {
        assertThat(field("publishedAt").getType()).isEqualTo(Instant.class);
        assertThat(field("createdAt").getType()).isEqualTo(Instant.class);
        assertThat(field("updatedAt").getType()).isEqualTo(Instant.class);
        assertThat(field("deletedAt").getType()).isEqualTo(Instant.class);
        assertThat(field("createdAt").getAnnotation(Column.class).nullable()).isFalse();
        assertThat(field("updatedAt").getAnnotation(Column.class).nullable()).isFalse();
        assertThat(field("deletedAt").getAnnotation(Column.class).nullable()).isTrue();
    }

    @Test
    void usesScalarIdsWithoutForeignEntityRelationships() {
        assertThat(field("authorUserId").getType()).isEqualTo(Long.class);
        assertThat(field("projectId").getType()).isEqualTo(Long.class);
        assertThat(field("categoryId").getType()).isEqualTo(Long.class);
        assertThat(field("coverFileId").getType()).isEqualTo(Long.class);
        assertThat(Arrays.stream(PostEntity.class.getDeclaredFields()))
                .noneMatch(field -> field.isAnnotationPresent(ManyToOne.class)
                        || field.isAnnotationPresent(OneToOne.class)
                        || field.isAnnotationPresent(OneToMany.class)
                        || field.isAnnotationPresent(ManyToMany.class));
    }

    @Test
    void hasProtectedJpaConstructorAndNoBroadSetters() throws NoSuchMethodException {
        Constructor<PostEntity> noArgConstructor = PostEntity.class.getDeclaredConstructor();

        assertThat(Modifier.isProtected(noArgConstructor.getModifiers())).isTrue();
        assertThat(Arrays.stream(PostEntity.class.getDeclaredMethods())
                .map(Method::getName))
                .noneMatch(name -> name.startsWith("set"));
    }

    @Test
    void controlledDraftCreationKeepsLifecycleAndTimestampsExplicit() {
        Instant now = Instant.parse("2026-08-09T10:00:00Z");
        PostEntity post = PostEntity.createDraft(
                10L,
                "Title",
                "title",
                null,
                Map.of(),
                PostVisibility.LAB,
                null,
                now
        );

        assertThat(post.getAuthorUserId()).isEqualTo(10L);
        assertThat(post.getStatus()).isEqualTo(PostStatus.DRAFT);
        assertThat(post.getCreatedAt()).isEqualTo(now);
        assertThat(post.getUpdatedAt()).isEqualTo(now);
        assertThat(post.getPublishedAt()).isNull();
        assertThat(post.getDeletedAt()).isNull();
    }

    @Test
    void controlledDraftUpdateChangesOnlyApprovedFieldsAndUpdatedAt() throws IllegalAccessException {
        Instant createdAt = Instant.parse("2026-08-01T10:00:00Z");
        Instant previousUpdatedAt = Instant.parse("2026-08-02T10:00:00Z");
        Instant publishedAt = Instant.parse("2026-08-03T10:00:00Z");
        Instant deletedAt = Instant.parse("2026-08-04T10:00:00Z");
        Instant transitionInstant = Instant.parse("2026-08-09T10:00:00Z");
        Map<String, Object> replacementContent = Map.of("type", "doc");
        PostEntity post = PostEntity.createDraft(
                10L,
                "Original",
                "immutable-slug",
                "Original excerpt",
                Map.of(),
                PostVisibility.LAB,
                7L,
                createdAt
        );
        set(post, "projectId", 20L);
        set(post, "coverFileId", 30L);
        set(post, "contentHtml", "<p>rendered</p>");
        set(post, "publishedAt", publishedAt);
        set(post, "deletedAt", deletedAt);
        set(post, "updatedAt", previousUpdatedAt);

        post.applyDraftUpdate(
                "Updated",
                null,
                replacementContent,
                PostVisibility.PUBLIC,
                null,
                transitionInstant
        );

        assertThat(post.getTitle()).isEqualTo("Updated");
        assertThat(post.getExcerpt()).isNull();
        assertThat(post.getContentJson()).isEqualTo(replacementContent).isNotSameAs(replacementContent);
        assertThat(post.getVisibility()).isEqualTo(PostVisibility.PUBLIC);
        assertThat(post.getCategoryId()).isNull();
        assertThat(post.getUpdatedAt()).isEqualTo(transitionInstant);
        assertThat(post.getSlug()).isEqualTo("immutable-slug");
        assertThat(post.getAuthorUserId()).isEqualTo(10L);
        assertThat(post.getStatus()).isEqualTo(PostStatus.DRAFT);
        assertThat(post.getProjectId()).isEqualTo(20L);
        assertThat(post.getCoverFileId()).isEqualTo(30L);
        assertThat(post.getContentHtml()).isEqualTo("<p>rendered</p>");
        assertThat(post.getPublishedAt()).isEqualTo(publishedAt);
        assertThat(post.getDeletedAt()).isEqualTo(deletedAt);
        assertThat(post.getCreatedAt()).isEqualTo(createdAt);
    }

    private static Set<String> fieldNames() {
        return Arrays.stream(PostEntity.class.getDeclaredFields())
                .map(Field::getName)
                .collect(java.util.stream.Collectors.toSet());
    }

    private static Field field(String name) {
        try {
            return PostEntity.class.getDeclaredField(name);
        } catch (NoSuchFieldException exception) {
            throw new AssertionError(exception);
        }
    }

    private static void set(PostEntity post, String fieldName, Object value) throws IllegalAccessException {
        Field field = field(fieldName);
        field.setAccessible(true);
        field.set(post, value);
    }

    private static void assertColumn(String fieldName, String columnName, boolean nullable, boolean unique, int length) throws NoSuchFieldException {
        Column column = field(fieldName).getAnnotation(Column.class);

        assertThat(column.name()).isEqualTo(columnName);
        assertThat(column.nullable()).isEqualTo(nullable);
        assertThat(column.unique()).isEqualTo(unique);
        assertThat(column.length()).isEqualTo(length);
    }

    private static void assertStringEnum(String fieldName, Class<?> enumType) throws NoSuchFieldException {
        Field field = field(fieldName);

        assertThat(field.getType()).isEqualTo(enumType);
        assertThat(field.isAnnotationPresent(Enumerated.class)).isTrue();
        assertThat(field.getAnnotation(Enumerated.class).value()).isEqualTo(EnumType.STRING);
    }
}
