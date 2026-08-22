package com.smartlab.service;

import com.smartlab.dto.request.ChangeProjectLeaderRequest;
import com.smartlab.dto.request.ChangeProjectLeadersRequest;
import com.smartlab.dto.request.CreateProjectRequest;
import com.smartlab.dto.request.UpdateProjectRequest;
import com.smartlab.dto.request.UpdateProjectLeadershipRequest;
import com.smartlab.dto.response.LeaderCandidateResponse;
import com.smartlab.dto.response.ProjectResponse;
import com.smartlab.dto.response.PublicPageResponse;

import java.util.List;

public interface ProjectService {
    List<ProjectResponse> list(String currentEmail);

    PublicPageResponse<ProjectResponse> listPublicRecruiting(int page, int size);

    ProjectResponse get(Long projectId, String currentEmail);

    ProjectResponse create(CreateProjectRequest request, String adminEmail);

    ProjectResponse update(Long projectId, UpdateProjectRequest request, String currentEmail);

    ProjectResponse changeLeader(Long projectId, ChangeProjectLeaderRequest request, String adminEmail);

    ProjectResponse replaceLeaders(Long projectId, ChangeProjectLeadersRequest request, String adminEmail);

    List<LeaderCandidateResponse> findLeaderCandidates(String query, String adminEmail);

    ProjectResponse updateLeadership(
            Long projectId,
            UpdateProjectLeadershipRequest request,
            String adminEmail
    );

    void delete(Long projectId, String adminEmail);
}
