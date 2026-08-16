package com.smartlab.service;

import com.smartlab.dto.request.AddProjectMemberRequest;
import com.smartlab.dto.response.ProjectMemberCandidateResponse;
import com.smartlab.dto.response.ProjectMemberResponse;
import com.smartlab.enums.ProjectMemberStatus;

import java.util.List;

public interface ProjectMemberService {
    List<ProjectMemberResponse> list(Long projectId, ProjectMemberStatus status, String currentEmail);

    ProjectMemberResponse add(Long projectId, AddProjectMemberRequest request, String currentEmail);

    void remove(Long projectId, String memberUserId, String currentEmail);

    List<ProjectMemberCandidateResponse> findCandidates(Long projectId, String query, String currentEmail);
}
