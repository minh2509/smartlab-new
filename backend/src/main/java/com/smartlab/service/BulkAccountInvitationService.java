package com.smartlab.service;

import com.smartlab.dto.request.BulkAccountInvitationRequest;
import com.smartlab.dto.response.BulkAccountInvitationBatchResponse;
import com.smartlab.dto.response.BulkAccountInvitationPreviewResponse;

public interface BulkAccountInvitationService {
    BulkAccountInvitationPreviewResponse preview(BulkAccountInvitationRequest request);
    BulkAccountInvitationBatchResponse provision(BulkAccountInvitationRequest request, String adminEmail);
    BulkAccountInvitationBatchResponse getBatch(String batchId);
    void resend(String batchId, String itemId, String adminEmail);
}
