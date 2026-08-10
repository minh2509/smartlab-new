package com.smartlab.service.impl;

import com.smartlab.entity.AuditLogEntity;
import com.smartlab.repo.AuditLogRepository;
import com.smartlab.service.AuditContextProvider;
import com.smartlab.service.AuditService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditServiceImplTest {
    @Mock AuditLogRepository repository;
    @Mock AuditContextProvider contextProvider;

    @Test
    void logBuildsExactDatabaseTimestampedEntityAndFlushes() {
        when(contextProvider.current()).thenReturn(new AuditContextProvider.AuditContext(4L, "10.0.0.1", "agent"));
        AuditService service = new AuditServiceImpl(repository, contextProvider);
        Map<String, Object> before = new LinkedHashMap<>(Map.of("status", "OLD"));
        service.log("ROLE_UPDATED", "ROLE", "9", before, Map.of("status", "NEW"));
        before.put("status", "MUTATED");

        ArgumentCaptor<AuditLogEntity> captor = ArgumentCaptor.forClass(AuditLogEntity.class);
        verify(repository).saveAndFlush(captor.capture());
        AuditLogEntity entity = captor.getValue();
        assertThat(entity.getActorUserId()).isEqualTo(4L);
        assertThat(entity.getBeforeJson()).containsEntry("status", "OLD");
        assertThat(entity.getAfterJson()).containsEntry("status", "NEW");
        assertThat(entity.getIpAddress()).isEqualTo("10.0.0.1");
        assertThat(entity.getUserAgent()).isEqualTo("agent");
        assertThat(entity.getCreatedAt()).isNull();
    }

    @Test
    void nullContextMetadataPersistsAndFailurePropagates() throws Exception {
        when(contextProvider.current()).thenReturn(new AuditContextProvider.AuditContext(null, null, null));
        DataIntegrityViolationException failure = new DataIntegrityViolationException("audit failed");
        when(repository.saveAndFlush(any())).thenThrow(failure);
        AuditService service = new AuditServiceImpl(repository, contextProvider);
        assertThatThrownBy(() -> service.log("POST_REVIEWED", "POST", "1", null, null)).isSameAs(failure);
        Transactional tx = AuditServiceImpl.class.getMethod("log", String.class, String.class, String.class,
                Map.class, Map.class).getAnnotation(Transactional.class);
        assertThat(tx.propagation()).isEqualTo(Propagation.REQUIRED);
    }
}
