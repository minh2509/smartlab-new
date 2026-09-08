package com.smartlab.dto.response;

import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class BulkAccountInvitationPreviewResponse {
    int requestedCount;
    int acceptedCount;
    int rejectedCount;
    List<BulkAccountInvitationItemResponse> items;
}
