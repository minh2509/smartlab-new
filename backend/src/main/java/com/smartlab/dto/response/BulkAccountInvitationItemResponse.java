package com.smartlab.dto.response;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class BulkAccountInvitationItemResponse {
    int sourceRow;
    String itemId;
    String fullName;
    String email;
    String status;
    String failureCode;
    String failureMessage;
    int resendCount;
}
