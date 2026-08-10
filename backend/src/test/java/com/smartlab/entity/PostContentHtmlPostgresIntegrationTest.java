package com.smartlab.entity;

import com.smartlab.enums.PostVisibility;
import com.smartlab.repo.PostRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class PostContentHtmlPostgresIntegrationTest {

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void persistsServerGeneratedContentHtmlInRealPostgres() {
        assertThat(jdbc.queryForObject(
                "select current_database()",
                String.class
        )).isEqualTo("smartlab_rich_editor_it");

        String tag = UUID.randomUUID()
                .toString()
                .replace("-", "");

        String expectedHtml =
                "<p>server-safe &amp; rendered</p>";

        PostEntity post = PostEntity.createDraft(
                null,
                "HTML " + tag,
                "html-" + tag,
                null,
                Map.of(
                        "type", "doc",
                        "body", "canonical"
                ),
                expectedHtml,
                PostVisibility.LAB,
                null,
                Instant.now()
        );

        PostEntity persisted =
                postRepository.saveAndFlush(post);

        String storedHtml = jdbc.queryForObject(
                "select content_html from posts where id = ?",
                String.class,
                persisted.getId()
        );

        assertThat(storedHtml)
                .isEqualTo(expectedHtml);
    }
}
