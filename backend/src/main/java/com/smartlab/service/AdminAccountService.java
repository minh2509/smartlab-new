package com.smartlab.service;

import com.smartlab.dto.request.AccountProvisionRequest;
import com.smartlab.dto.request.InvitationAcceptRequest;
import com.smartlab.dto.request.PermissionOverrideRequest;
import com.smartlab.dto.response.AccountResponse;
import com.smartlab.dto.response.InvitationResponse;
import com.smartlab.dto.response.PageResponse;
import com.smartlab.entity.UserEntity;

import java.util.Set;

public interface AdminAccountService {
    PageResponse<AccountResponse> listAccounts(int page, int size);

    InvitationResponse provision(AccountProvisionRequest request, String adminUserId);

    InvitationResponse resendInvite(String email, String adminUserId);

    AccountResponse acceptInvite(InvitationAcceptRequest request);

    AccountResponse updateRoles(String userId, Set<String> roleCodes, String adminUserId);

    AccountResponse setActive(String userId, boolean active);

    AccountResponse setPermissionOverride(
            String userId,
            String permissionCode,
            PermissionOverrideRequest request,
            String adminUserId
    );

    AccountResponse removePermissionOverride(String userId, String permissionCode);

    AccountResponse toResponse(UserEntity user);
}
