package com.smartlab.dto.response;

import lombok.Builder;
import lombok.Value;

import java.time.Instant;
import java.util.List;

@Value
@Builder
public class BulkAccountInvitationBatchResponse {
    String batchId;
    String status;
    int requestedCount;
    int acceptedCount;
    int rejectedCount;
    String createdBy;
    Instant createdAt;
    Instant completedAt;
    List<String> roleCodes;
    List<BulkAccountInvitationItemResponse> items;
}
