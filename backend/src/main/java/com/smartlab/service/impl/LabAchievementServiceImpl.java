package com.smartlab.service.impl;

import com.smartlab.dto.request.CreateLabAchievementRequest;
import com.smartlab.dto.request.UpdateLabAchievementRequest;
import com.smartlab.dto.response.AchievementYearCountResponse;
import com.smartlab.dto.response.AdminLabAchievementResponse;
import com.smartlab.dto.response.LabAchievementResponse;
import com.smartlab.dto.response.LabAchievementFileResponse;
import com.smartlab.dto.response.PublicPageResponse;
import com.smartlab.dto.response.PublicRelatedProjectResponse;
import com.smartlab.entity.LabAchievementEntity;
import com.smartlab.entity.LabAchievementFileEntity;
import com.smartlab.entity.ProjectEntity;
import com.smartlab.entity.StoredFileEntity;
import com.smartlab.enums.AchievementType;
import com.smartlab.repo.LabAchievementRepository;
import com.smartlab.repo.LabAchievementFileRepository;
import com.smartlab.repo.ProjectRepository;
import com.smartlab.repo.StoredFileRepository;
import com.smartlab.service.FileService;
import com.smartlab.service.LabAchievementService;
import com.smartlab.service.PostContentFileService;
import com.smartlab.validation.AchievementYearPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.multipart.MultipartFile;

import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class LabAchievementServiceImpl implements LabAchievementService {
    private static final int PUBLIC_PAGE_SIZE_MAX = 24;
    private static final int ADMIN_PAGE_SIZE_MAX = 100;
    private static final int ADMIN_QUERY_MAX_LENGTH = 200;
    private final LabAchievementRepository achievementRepository;
    private final ProjectRepository projectRepository;
    private final LabAchievementFileRepository achievementFileRepository;
    private final StoredFileRepository storedFileRepository;
    private final FileService fileService;
    private final PostContentFileService postContentFileService;

    @Override
    @Transactional(readOnly = true)
    public List<AchievementYearCountResponse> listPublicYears() {
        return achievementRepository.findPublicYearCounts().stream()
                .map(row -> new AchievementYearCountResponse(((Number) row[0]).intValue(), ((Number) row[1]).longValue()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public PublicPageResponse<LabAchievementResponse> listPublic(Integer year, int page, int size) {
        validatePublicPage(year, page, size);
        Integer selectedYear = year == null ? achievementRepository.findNewestPublicYear() : year;
        if (selectedYear == null) return new PublicPageResponse<>(List.of(), page, size, 0, 0);
        Page<LabAchievementResponse> result = achievementRepository.findPublicByYear(selectedYear, PageRequest.of(page, size))
                .map(this::toResponse);
        return PublicPageResponse.from(result);
    }

    @Override
    @Transactional(readOnly = true)
    public PublicPageResponse<AdminLabAchievementResponse> listAdmin(Integer year, AchievementType type, Boolean isPublic,
            String q, int page, int size) {
        String normalizedQuery = optional(q);
        validateAdminPage(year, normalizedQuery, page, size);
        String repositoryQuery = normalizedQuery == null ? "" : normalizedQuery;
        return PublicPageResponse.from(achievementRepository.findActiveForAdmin(year, type, isPublic, repositoryQuery,
                PageRequest.of(page, size)).map(this::toAdminResponse));
    }

    @Override
    @Transactional
    public AdminLabAchievementResponse create(CreateLabAchievementRequest request) {
        Integer year = requireYear(request.getAchievementYear());
        LocalDate date = request.getAchievementDate();
        validateDateYear(date, year);
        LabAchievementEntity saved = achievementRepository.saveAndFlush(LabAchievementEntity.create(
                required(request.getTitle(), "Title is required"), optional(request.getSummary()),
                requireType(request.getAchievementType()), year, date, optionalUrl(request.getEvidenceUrl()),
                optional(request.getRecognizingOrganization()),
                resolveProject(request.getRelatedProjectId()), Boolean.TRUE.equals(request.getIsPublic()), Instant.now()
        ));
        return toAdminResponse(saved);
    }

    @Override
    @Transactional
    public AdminLabAchievementResponse update(Long id, UpdateLabAchievementRequest request) {
        LabAchievementEntity achievement = findActive(id);
        Integer year = request.getAchievementYear() == null ? achievement.getAchievementYear() : requireYear(request.getAchievementYear());
        LocalDate date = request.isAchievementDatePresent() ? request.getAchievementDate() : achievement.getAchievementDate();
        validateDateYear(date, year);
        ProjectEntity relatedProject = request.isRelatedProjectIdPresent()
                ? resolveProject(request.getRelatedProjectId()) : achievement.getRelatedProject();
        achievement.update(
                request.getTitle() == null ? achievement.getTitle() : required(request.getTitle(), "Title is required"),
                request.isSummaryPresent() ? optional(request.getSummary()) : achievement.getSummary(),
                request.getAchievementType() == null ? achievement.getAchievementType() : requireType(request.getAchievementType()),
                year, date,
                request.isEvidenceUrlPresent() ? optionalUrl(request.getEvidenceUrl()) : achievement.getEvidenceUrl(),
                request.isRecognizingOrganizationPresent() ? optional(request.getRecognizingOrganization()) : achievement.getRecognizingOrganization(),
                relatedProject, request.getIsPublic() == null ? achievement.getIsPublic() : request.getIsPublic(), Instant.now()
        );
        return toAdminResponse(achievementRepository.saveAndFlush(achievement));
    }

    @Override
    @Transactional
    public void delete(Long id) {
        LabAchievementEntity achievement = findActive(id);
        achievement.softDelete(Instant.now());
        achievementRepository.saveAndFlush(achievement);
    }

    @Override
    @Transactional(readOnly = true)
    public List<LabAchievementFileResponse> listFiles(Long achievementId) {
        findActive(achievementId);
        return achievementFileRepository.findActiveByAchievementId(achievementId).stream().map(this::toFileResponse).toList();
    }

    @Override
    @Transactional
    public LabAchievementFileResponse uploadFile(Long achievementId, MultipartFile file, String label, String email) {
        LabAchievementEntity achievement = findActive(achievementId);
        String normalizedLabel = optional(label);
        Long fileId = fileService.upload(file, "PRIVATE", null, email).getId();
        StoredFileEntity storedFile = storedFileRepository.findByIdAndDeletedAtIsNull(fileId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "File not found"));
        Integer maxSortOrder = achievementFileRepository.findMaxActiveSortOrder(achievementId);
        LabAchievementFileEntity mapping = achievementFileRepository.saveAndFlush(LabAchievementFileEntity.create(
                achievement, storedFile, normalizedLabel, (maxSortOrder == null ? -1 : maxSortOrder) + 1, Instant.now()));
        return toFileResponse(mapping);
    }

    @Override
    @Transactional
    public void detachFile(Long achievementId, Long attachmentId) {
        findActive(achievementId);
        LabAchievementFileEntity attachment = achievementFileRepository.findActiveByIdAndAchievementId(attachmentId, achievementId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Achievement attachment not found"));
        attachment.softDetach(Instant.now());
        achievementFileRepository.saveAndFlush(attachment);
    }

    @Override
    @Transactional(readOnly = true)
    public FileDownload downloadPublicFile(Long achievementId, Long attachmentId) {
        LabAchievementEntity achievement = achievementRepository.findByIdAndDeletedAtIsNull(achievementId)
                .filter(value -> Boolean.TRUE.equals(value.getIsPublic()))
                .orElseThrow(this::achievementAttachmentNotFound);
        LabAchievementFileEntity attachment = achievementFileRepository.findPublicActiveByIdAndAchievementId(attachmentId, achievementId)
                .orElseThrow(this::achievementAttachmentNotFound);
        PostContentFileService.FileMetadata metadata = postContentFileService.findActiveMetadata(attachment.getFile().getId())
                .orElseThrow(this::achievementAttachmentNotFound);
        if (!"PUBLIC".equalsIgnoreCase(metadata.accessScope())) {
            throw achievementAttachmentNotFound();
        }
        PostContentFileService.DownloadedContent content = postContentFileService.downloadActiveContent(metadata.id());
        return new FileDownload(content.content(), content.mimeType(), content.originalName());
    }

    private void validatePublicPage(Integer year, int page, int size) {
        if (year != null) requirePublicYear(year);
        if (page < 0) throw badRequest("Page must not be negative");
        if (size < 1 || size > PUBLIC_PAGE_SIZE_MAX) throw badRequest("Size must be between 1 and 24");
    }

    private void validateAdminPage(Integer year, String q, int page, int size) {
        if (year != null) requireYear(year);
        if (page < 0) throw badRequest("Page must not be negative");
        if (size < 1 || size > ADMIN_PAGE_SIZE_MAX) throw badRequest("Size must be between 1 and 100");
        if (q != null && q.length() > ADMIN_QUERY_MAX_LENGTH) throw badRequest("Query must be at most 200 characters");
    }

    private LabAchievementEntity findActive(Long id) {
        return achievementRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Achievement not found"));
    }

    private ProjectEntity resolveProject(Long projectId) {
        if (projectId == null) return null;
        return projectRepository.findByIdAndDeletedAtIsNull(projectId)
                .orElseThrow(() -> badRequest("Related project not found"));
    }

    private AchievementType requireType(AchievementType type) {
        if (type == null) throw badRequest("Achievement type is required");
        return type;
    }

    private Integer requireYear(Integer year) {
        if (!AchievementYearPolicy.isValid(year)) throw badRequest(AchievementYearPolicy.ERROR_MESSAGE);
        return year;
    }

    private Integer requirePublicYear(Integer year) {
        if (year < 1900 || year > 2100) throw badRequest("Achievement year must be between 1900 and 2100");
        return year;
    }

    private void validateDateYear(LocalDate date, Integer year) {
        if (date != null && date.getYear() != year) throw badRequest("Achievement date year must equal achievement year");
    }

    private String required(String value, String message) {
        String normalized = optional(value);
        if (normalized == null) throw badRequest(message);
        return normalized;
    }

    private String optional(String value) {
        if (value == null) return null;
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private String optionalUrl(String value) {
        String normalized = optional(value);
        if (normalized == null) return null;
        try {
            URI uri = URI.create(normalized);
            if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme())) || uri.getHost() == null) {
                throw badRequest("Evidence URL must be an HTTP(S) URL");
            }
        } catch (IllegalArgumentException exception) {
            throw badRequest("Evidence URL must be an HTTP(S) URL");
        }
        return normalized;
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private ResponseStatusException achievementAttachmentNotFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Achievement attachment not found");
    }

    private LabAchievementResponse toResponse(LabAchievementEntity achievement) {
        ProjectEntity project = achievement.getRelatedProject();
        PublicRelatedProjectResponse publicProject = project != null && project.getDeletedAt() == null && Boolean.TRUE.equals(project.getIsPublic())
                ? new PublicRelatedProjectResponse(project.getId(), project.getCode(), project.getName()) : null;
        return LabAchievementResponse.builder()
                .id(achievement.getId()).title(achievement.getTitle()).summary(achievement.getSummary())
                .achievementType(achievement.getAchievementType()).achievementYear(achievement.getAchievementYear())
                .achievementDate(achievement.getAchievementDate()).evidenceUrl(achievement.getEvidenceUrl())
                .recognizingOrganization(achievement.getRecognizingOrganization())
                .relatedProject(publicProject)
                .isPublic(achievement.getIsPublic()).createdAt(achievement.getCreatedAt()).updatedAt(achievement.getUpdatedAt())
                .build();
    }

    private AdminLabAchievementResponse toAdminResponse(LabAchievementEntity achievement) {
        return new AdminLabAchievementResponse(achievement.getId(), achievement.getTitle(), achievement.getSummary(),
                achievement.getAchievementType(), achievement.getAchievementYear(), achievement.getAchievementDate(),
                achievement.getEvidenceUrl(), achievement.getRecognizingOrganization(), achievement.getRelatedProjectId(), achievement.getIsPublic(),
                achievement.getCreatedAt(), achievement.getUpdatedAt());
    }

    private LabAchievementFileResponse toFileResponse(LabAchievementFileEntity attachment) {
        StoredFileEntity file = attachment.getFile();
        return new LabAchievementFileResponse(attachment.getId(), file.getId(), file.getOriginalName(), file.getMimeType(),
                file.getSizeBytes(), attachment.getLabel(), attachment.getSortOrder(), attachment.getCreatedAt());
    }
}
