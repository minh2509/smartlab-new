package com.smartlab.service;

import com.smartlab.dto.request.AdminUpdateMemberProfileRequest;
import com.smartlab.dto.request.UpdateMemberProfileRequest;
import com.smartlab.dto.response.MemberProfileResponse;

import java.util.List;

public interface MemberProfileService {
    MemberProfileResponse getOwnProfile(String email);

    MemberProfileResponse updateOwnProfile(String email, UpdateMemberProfileRequest request);

    List<MemberProfileResponse> listMembers(String keyword, String fieldCode, String status);

    List<MemberProfileResponse> listAllMembers();

    MemberProfileResponse updateMember(String userId, AdminUpdateMemberProfileRequest request);
}
