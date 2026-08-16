package com.smartlab.service.impl;

import com.smartlab.entity.NotificationEntity;
import com.smartlab.entity.UserEntity;
import com.smartlab.repo.NotificationRepository;
import com.smartlab.repo.UserRepository;
import com.smartlab.service.NotificationRelated;
import com.smartlab.service.NotificationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@SpringBootTest
class NotificationPostgresIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired NotificationRepository notifications;
    @Autowired NotificationService service;
    @MockitoBean UserRepository users;
    List<Long> userIds = new ArrayList<>();
    long baselineUsers, baselineNotifications, baselinePosts, baselineReviews;
    @BeforeEach void before() {
        assertThat(jdbc.queryForObject("select current_database()", String.class)).isEqualTo("smartlab_rich_editor_it");
        baselineUsers = count("tbl_user"); baselineNotifications = count("notifications"); baselinePosts = count("posts"); baselineReviews = count("post_reviews");
    }
    @AfterEach void after() {
        userIds.forEach(id -> jdbc.update("delete from notifications where recipient_user_id = ? or actor_user_id = ?", id, id));
        userIds.forEach(id -> jdbc.update("delete from tbl_user where id = ?", id));
        assertThat(count("tbl_user")).isEqualTo(baselineUsers); assertThat(count("notifications")).isEqualTo(baselineNotifications);
        assertThat(count("posts")).isEqualTo(baselinePosts); assertThat(count("post_reviews")).isEqualTo(baselineReviews);
    }
    @Test void pg1ToPg7IsolationMutationsAndInternalNotify() {
        UserEntity a = fixture("a"), b = fixture("b"); canonical(a); canonical(b);
        Instant earlier = Instant.parse("2026-08-10T10:00:00Z"), later = earlier.plusSeconds(1);
        NotificationEntity aOld = save(a.getId(), null, "OLD", earlier), aNew = save(a.getId(), null, "NEW", later), bRow = save(b.getId(), null, "B", later);
        NotificationEntity deleted = save(a.getId(), null, "DELETED", later.plusSeconds(1)); deleted.softDelete(later.plusSeconds(2)); notifications.saveAndFlush(deleted);
        assertThat(service.getNotifications(a.getEmail())).extracting(n -> n.type()).containsExactly("NEW", "OLD");
        service.markRead(a.getEmail(), aOld.getId()); assertThat(notifications.findById(aOld.getId()).orElseThrow().isRead()).isTrue();
        assertThatThrownBy(() -> service.markRead(a.getEmail(), bRow.getId())).isInstanceOf(ResponseStatusException.class);
        assertThat(notifications.findById(bRow.getId()).orElseThrow().isRead()).isFalse();
        service.markAllRead(a.getEmail()); assertThat(notifications.findById(aNew.getId()).orElseThrow().isRead()).isTrue(); assertThat(notifications.findById(bRow.getId()).orElseThrow().isRead()).isFalse();
        service.softDelete(a.getEmail(), aOld.getId()); assertThat(notifications.findById(aOld.getId()).orElseThrow().getDeletedAt()).isNotNull();
        assertThatThrownBy(() -> service.softDelete(a.getEmail(), bRow.getId())).isInstanceOf(ResponseStatusException.class); assertThat(notifications.findById(bRow.getId()).orElseThrow().getDeletedAt()).isNull();
        service.notify(a.getId(), "INTERNAL", "message", new NotificationRelated(b.getId(), "POST", 9L, "/posts/9"), earlier);
        NotificationEntity internal = notifications.findByRecipientUserIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(a.getId()).stream().filter(n -> n.getType().equals("INTERNAL")).findFirst().orElseThrow();
        assertThat(internal.getActorUserId()).isEqualTo(b.getId()); assertThat(internal.getRelatedType()).isEqualTo("POST"); assertThat(internal.getRelatedId()).isEqualTo(9L); assertThat(internal.getTargetUrl()).isEqualTo("/posts/9"); assertThat(internal.isRead()).isFalse(); assertThat(internal.getCreatedAt()).isEqualTo(earlier);
    }
    private void canonical(UserEntity user) { when(users.findByEmail(user.getEmail())).thenReturn(Optional.of(user)); }
    private NotificationEntity save(Long recipient, Long actor, String type, Instant time) { return notifications.saveAndFlush(NotificationEntity.create(recipient, actor, type, type, null, null, null, time)); }
    private UserEntity fixture(String tag) { String u = UUID.randomUUID().toString().replace("-", ""); Long id = jdbc.queryForObject("insert into tbl_user(user_id,name,email,password,is_active,is_account_verified,reset_otp_expire_at) values(?,?,?,?,true, true, NULL) returning id", Long.class, "n2"+u, "N2", "n2-"+tag+"-"+u+"@test", "x"); userIds.add(id); return UserEntity.builder().id(id).email("n2-"+tag+"-"+u+"@test").isActive(true).build(); }
    private long count(String table) { return jdbc.queryForObject("select count(*) from " + table, Long.class); }
}
