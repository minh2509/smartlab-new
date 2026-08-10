package com.smartlab.service.impl;

import com.smartlab.entity.AuditLogEntity;
import com.smartlab.repo.AuditLogRepository;
import com.smartlab.service.AuditContextProvider;
import com.smartlab.service.AuditService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class AuditServiceImpl implements AuditService {
    private final AuditLogRepository auditLogRepository;
    private final AuditContextProvider auditContextProvider;

    @Override
    @Transactional
    public void log(String action, String targetType, String targetId,
                    Map<String, Object> beforeJson, Map<String, Object> afterJson) {
        AuditContextProvider.AuditContext context = auditContextProvider.current();
        auditLogRepository.saveAndFlush(AuditLogEntity.create(
                context.actorUserId(), action, targetType, targetId,
                beforeJson, afterJson, context.ipAddress(), context.userAgent()));
    }
}
