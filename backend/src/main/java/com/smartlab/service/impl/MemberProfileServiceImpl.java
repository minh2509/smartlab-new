package com.smartlab.service.impl;

import com.smartlab.dto.request.AdminUpdateMemberProfileRequest;
import com.smartlab.dto.request.UpdateMemberProfileRequest;
import com.smartlab.dto.response.FileResponse;
import com.smartlab.dto.response.MemberProfileResponse;
import com.smartlab.dto.response.ResearchFieldResponse;
import com.smartlab.entity.MemberProfileEntity;
import com.smartlab.entity.ResearchFieldEntity;
import com.smartlab.entity.StoredFileEntity;
import com.smartlab.entity.UserEntity;
import com.smartlab.repo.MemberProfileRepository;
import com.smartlab.repo.ResearchFieldRepository;
import com.smartlab.repo.StoredFileRepository;
import com.smartlab.repo.UserRepository;
import com.smartlab.service.MemberProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class MemberProfileServiceImpl implements MemberProfileService {
    private final UserRepository userRepository;
    private final MemberProfileRepository memberProfileRepository;
    private final StoredFileRepository storedFileRepository;
    private final ResearchFieldRepository researchFieldRepository;

    @Override
    @Transactional
    public MemberProfileResponse getOwnProfile(String email) {
        UserEntity user = findUserByEmail(email);
        return toResponse(ensureProfile(user), true);
    }

    @Override
    @Transactional
    public MemberProfileResponse updateOwnProfile(String email, UpdateMemberProfileRequest request) {
        UserEntity user = findUserByEmail(email);
        MemberProfileEntity profile = ensureProfile(user);
        applyCommonChanges(profile, request.getPhone(), request.getPublicEmail(), request.getBio(),
                request.getAvatarFileId(), request.getRemoveAvatar(),
                request.getResearchFieldIds(), user, false);
        return toResponse(memberProfileRepository.save(profile), true);
    }

    @Override
    @Transactional(readOnly = true)
    public List<MemberProfileResponse> listMembers(String keyword, String fieldCode, String status) {
        String normalizedStatus = status == null || status.isBlank() ? "ACTIVE" : status.trim().toUpperCase(Locale.ROOT);
        if (!Set.of("ACTIVE", "ALUMNI").contains(normalizedStatus)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Public member status must be ACTIVE or ALUMNI");
        }
        String normalizedKeyword = keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT);
        String normalizedField = fieldCode == null ? "" : fieldCode.trim().toUpperCase(Locale.ROOT);
        return memberProfileRepository.findByActiveStatusOrderByFeaturedOrderAscIdAsc(normalizedStatus).stream()
                .filter(profile -> normalizedKeyword.isBlank()
                        || profile.getUser().getName().toLowerCase(Locale.ROOT).contains(normalizedKeyword)
                        || (profile.getBio() != null && profile.getBio().toLowerCase(Locale.ROOT).contains(normalizedKeyword)))
                .filter(profile -> normalizedField.isBlank()
                        || profile.getResearchFields().stream().anyMatch(field -> normalizedField.equals(field.getCode())))
                .map(profile -> toResponse(profile, false))
                .toList();
    }

    @Override
    @Transactional
    public List<MemberProfileResponse> listAllMembers() {
        return userRepository.findAll().stream()
                .map(this::ensureProfile)
                .map(profile -> toResponse(profile, true))
                .toList();
    }

    @Override
    @Transactional
    public MemberProfileResponse updateMember(String userId, AdminUpdateMemberProfileRequest request) {
        UserEntity user = userRepository.findByUserId(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Member not found"));
        MemberProfileEntity profile = ensureProfile(user);
        applyCommonChanges(profile, request.getPhone(), request.getPublicEmail(), request.getBio(),
                request.getAvatarFileId(), request.getRemoveAvatar(),
                request.getResearchFieldIds(), user, true);
        if (request.getActiveStatus() != null) {
            String activeStatus = request.getActiveStatus().trim().toUpperCase(Locale.ROOT);
            if (!Set.of("ACTIVE", "INACTIVE", "ALUMNI").contains(activeStatus)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid member status");
            }
            profile.setActiveStatus(activeStatus);
        }
        if (request.getIsFeatured() != null) {
            profile.setIsFeatured(request.getIsFeatured());
        }
        if (Boolean.TRUE.equals(request.getClearFeaturedOrder())) {
            profile.setFeaturedOrder(null);
        } else if (request.getFeaturedOrder() != null && request.getFeaturedOrder() < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Featured order must not be negative");
        } else if (request.getFeaturedOrder() != null) {
            profile.setFeaturedOrder(request.getFeaturedOrder());
        }
        return toResponse(memberProfileRepository.save(profile), true);
    }

    private void applyCommonChanges(
            MemberProfileEntity profile,
            String phone,
            String publicEmail,
            String bio,
            Long avatarFileId,
            Boolean removeAvatar,
            Set<Long> researchFieldIds,
            UserEntity user,
            boolean admin
    ) {
        if (phone != null) profile.setPhone(phone.trim());
        if (publicEmail != null) profile.setPublicEmail(publicEmail.trim());
        if (bio != null) profile.setBio(bio.trim());
        if (Boolean.TRUE.equals(removeAvatar)) {
            profile.setAvatarFile(null);
        } else if (avatarFileId != null) {
            StoredFileEntity avatar = storedFileRepository.findByIdAndDeletedAtIsNull(avatarFileId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Avatar file not found"));
            if (avatar.getMimeType() == null || !avatar.getMimeType().toLowerCase(Locale.ROOT).startsWith("image/")) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Avatar must be an image");
            }
            boolean owned = avatar.getOwnerUser() != null && Objects.equals(avatar.getOwnerUser().getId(), user.getId());
            if (!owned && !admin) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Avatar file belongs to another user");
            }
            profile.setAvatarFile(avatar);
        }
        if (researchFieldIds != null) {
            profile.setResearchFields(resolveResearchFields(researchFieldIds));
        }
    }

    private Set<ResearchFieldEntity> resolveResearchFields(Set<Long> ids) {
        List<ResearchFieldEntity> fields = researchFieldRepository.findAllById(ids);
        if (fields.size() != ids.size() || fields.stream().anyMatch(field -> !Boolean.TRUE.equals(field.getIsActive()))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "One or more research fields are invalid or inactive");
        }
        return new LinkedHashSet<>(fields);
    }

    private MemberProfileEntity ensureProfile(UserEntity user) {
        return memberProfileRepository.findByUserId(user.getId())
                .orElseGet(() -> memberProfileRepository.save(MemberProfileEntity.create(user)));
    }

    private UserEntity findUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));
    }

    private MemberProfileResponse toResponse(MemberProfileEntity profile, boolean privateView) {
        UserEntity user = profile.getUser();
        return MemberProfileResponse.builder()
                .userId(user.getUserId())
                .name(user.getName())
                .email(privateView ? user.getEmail() : null)
                .publicEmail(profile.getPublicEmail())
                .phone(privateView ? profile.getPhone() : null)
                .bio(profile.getBio())
                .joinedLabAt(profile.getJoinedLabAt())
                .activeStatus(profile.getActiveStatus())
                .isFeatured(profile.getIsFeatured())
                .featuredOrder(profile.getFeaturedOrder())
                .avatar(toFileResponse(profile.getAvatarFile()))
                .researchFields(profile.getResearchFields().stream().map(this::toResearchFieldResponse).toList())
                .build();
    }

    private FileResponse toFileResponse(StoredFileEntity file) {
        if (file == null || file.getDeletedAt() != null) return null;
        return FileResponse.builder()
                .id(file.getId())
                .originalName(file.getOriginalName())
                .mimeType(file.getMimeType())
                .sizeBytes(file.getSizeBytes())
                .accessScope(file.getAccessScope())
                .description(file.getDescription())
                .createdAt(file.getCreatedAt())
                .build();
    }

    private ResearchFieldResponse toResearchFieldResponse(ResearchFieldEntity field) {
        return ResearchFieldResponse.builder()
                .id(field.getId())
                .code(field.getCode())
                .name(field.getName())
                .description(field.getDescription())
                .isActive(field.getIsActive())
                .build();
    }
}
