package com.smartlab.dto.request;

import lombok.Data;

@Data
public class BulkAccountInvitationRowRequest {
    private String fullName;
    private String email;
}
