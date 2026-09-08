package com.smartlab.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;
import java.util.Set;

@Data
public class BulkAccountInvitationRequest {
    @Valid
    @NotEmpty(message = "Provide at least one account row")
    @Size(max = 100, message = "A batch may contain at most 100 account rows")
    private List<BulkAccountInvitationRowRequest> items;

    @NotEmpty(message = "Select at least one active role")
    private Set<String> roleCodes;
}
