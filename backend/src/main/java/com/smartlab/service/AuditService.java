package com.smartlab.service;

import java.util.Map;

public interface AuditService {
    void log(String action, String targetType, String targetId,
             Map<String, Object> beforeJson, Map<String, Object> afterJson);
}
