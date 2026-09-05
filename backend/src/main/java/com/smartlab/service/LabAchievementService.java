package com.smartlab.service;

import com.smartlab.dto.request.CreateLabAchievementRequest;
import com.smartlab.dto.request.UpdateLabAchievementRequest;
import com.smartlab.dto.response.AchievementYearCountResponse;
import com.smartlab.dto.response.AdminLabAchievementResponse;
import com.smartlab.dto.response.LabAchievementResponse;
import com.smartlab.dto.response.LabAchievementFileResponse;
import com.smartlab.dto.response.PublicPageResponse;
import com.smartlab.enums.AchievementType;

import java.util.List;
import org.springframework.web.multipart.MultipartFile;

public interface LabAchievementService {
    List<AchievementYearCountResponse> listPublicYears();
    PublicPageResponse<LabAchievementResponse> listPublic(Integer year, int page, int size);
    PublicPageResponse<AdminLabAchievementResponse> listAdmin(Integer year, AchievementType type, Boolean isPublic, String q, int page, int size);
    AdminLabAchievementResponse create(CreateLabAchievementRequest request);
    AdminLabAchievementResponse update(Long id, UpdateLabAchievementRequest request);
    void delete(Long id);
    List<LabAchievementFileResponse> listFiles(Long achievementId);
    LabAchievementFileResponse uploadFile(Long achievementId, MultipartFile file, String label, String email);
    void detachFile(Long achievementId, Long attachmentId);
    FileDownload downloadPublicFile(Long achievementId, Long attachmentId);

    record FileDownload(byte[] content, String mimeType, String originalName) { }
}
