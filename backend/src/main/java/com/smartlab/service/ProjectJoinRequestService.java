package com.smartlab.service;

import com.smartlab.dto.request.CreateProjectJoinRequest;
import com.smartlab.dto.request.ReviewProjectJoinRequest;
import com.smartlab.dto.response.ProjectJoinRequestResponse;
import com.smartlab.enums.ProjectJoinRequestStatus;

import java.util.List;
import java.util.Optional;

public interface ProjectJoinRequestService {
    ProjectJoinRequestResponse create(Long projectId, CreateProjectJoinRequest request, String currentEmail);

    Optional<ProjectJoinRequestResponse> getLatestMine(Long projectId, String currentEmail);

    void cancelMine(Long projectId, String currentEmail);

    List<ProjectJoinRequestResponse> listForManagement(
            Long projectId,
            ProjectJoinRequestStatus status,
            String currentEmail
    );

    ProjectJoinRequestResponse review(
            Long projectId,
            Long requestId,
            ReviewProjectJoinRequest request,
            String currentEmail
    );
}
