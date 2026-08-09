package com.smartlab.entity;

import com.smartlab.enums.PostVisibility;
import com.smartlab.repo.PostRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@SpringBootTest
@Transactional
class PostJsonbMapIntegrationTest {

    private static final String TARGET_DATABASE = "smartlab_rich_editor_it";

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void nestedAndEmptyObjectMapsRoundTripThroughPostgreSqlJsonb() {
        assumeTrue(TARGET_DATABASE.equals(currentDatabase()),
                "R16 JSONB integration test requires the isolated PostgreSQL database");

        Map<String, Object> nested = new LinkedHashMap<>();
        nested.put("enabled", true);
        nested.put("nullable", null);
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("type", "doc");
        content.put("nested", nested);
        content.put("items", List.of(1, "x", false));
        content.put("count", 42);

        PostEntity nestedPost = persist(content, "nested");
        PostEntity emptyPost = persist(new LinkedHashMap<>(), "empty");

        entityManager.clear();

        assertThat(postRepository.findById(nestedPost.getId()).orElseThrow().getContentJson())
                .isEqualTo(content);
        assertThat(postRepository.findById(emptyPost.getId()).orElseThrow().getContentJson())
                .isEmpty();
        assertThat(jdbc.queryForObject("""
                select data_type
                from information_schema.columns
                where table_schema = 'public'
                  and table_name = 'posts'
                  and column_name = 'content_json'
                """, String.class)).isEqualTo("jsonb");
    }

    private PostEntity persist(Map<String, Object> contentJson, String tag) {
        Instant now = Instant.now();
        PostEntity post = PostEntity.createDraft(
                null,
                "R16 JSONB " + tag,
                "r16-jsonb-" + tag + "-" + UUID.randomUUID(),
                null,
                contentJson,
                PostVisibility.LAB,
                null,
                now
        );
        return postRepository.saveAndFlush(post);
    }

    private String currentDatabase() {
        return jdbc.queryForObject("select current_database()", String.class);
    }
}
