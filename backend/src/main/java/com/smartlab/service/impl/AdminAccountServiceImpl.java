package com.smartlab.service.impl;

import com.smartlab.entity.AccountInvitationEntity;
import com.smartlab.entity.EmailOutboxEntity;
import com.smartlab.entity.MemberProfileEntity;
import com.smartlab.entity.PermissionEntity;
import com.smartlab.entity.RoleEntity;
import com.smartlab.entity.UserEntity;
import com.smartlab.entity.UserPermissionOverrideEntity;
import com.smartlab.entity.UserRoleEntity;
import com.smartlab.enums.InvitationStatus;
import com.smartlab.enums.EmailOutboxStatus;
import com.smartlab.dto.request.AccountProvisionRequest;
import com.smartlab.dto.request.AccountUpdateRequest;
import com.smartlab.dto.response.AccountResponse;
import com.smartlab.dto.request.InvitationAcceptRequest;
import com.smartlab.dto.response.InvitationResponse;
import com.smartlab.dto.response.PageResponse;
import com.smartlab.dto.request.PermissionOverrideRequest;
import com.smartlab.dto.response.PermissionOverrideResponse;
import com.smartlab.repo.AccountInvitationRepository;
import com.smartlab.repo.EmailOutboxRepository;
import com.smartlab.repo.MemberProfileRepository;
import com.smartlab.repo.PermissionRepository;
import com.smartlab.repo.RoleRepository;
import com.smartlab.repo.UserPermissionOverrideRepository;
import com.smartlab.repo.UserRepository;
import com.smartlab.repo.UserRoleRepository;
import com.smartlab.service.AdminAccountService;
import com.smartlab.service.AuditService;
import com.smartlab.service.PermissionService;
import com.smartlab.service.TokenHashService;
import com.smartlab.service.UserSessionService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static com.smartlab.service.AuditVocabulary.*;

@Service
@RequiredArgsConstructor
public class AdminAccountServiceImpl implements AdminAccountService {
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final UserRoleRepository userRoleRepository;
    private final UserPermissionOverrideRepository userPermissionOverrideRepository;
    private final AccountInvitationRepository accountInvitationRepository;
    private final MemberProfileRepository memberProfileRepository;
    private final PermissionService permissionService;
    private final EmailOutboxRepository emailOutboxRepository;
    private final AccountInvitationOutboxStateService outboxStateService;
    private final UserSessionService userSessionService;
    private final TokenHashService tokenHashService;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;
    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${smartlab.invite.ttl-hours:72}")
    private long inviteTtlHours;

    @Transactional(readOnly = true)
    @Override
    public PageResponse<AccountResponse> listAccounts(
            int page,
            int size,
            String query,
            Boolean active,
            Boolean verified,
            String roleCode
    ) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 50);
        String safeQuery = query == null ? "" : query.trim();
        String safeRoleCode = roleCode == null ? "" : roleCode.trim().toUpperCase();
        return PageResponse.from(userRepository.findAccounts(
                        safeQuery, active, verified, safeRoleCode, PageRequest.of(safePage, safeSize))
                .map(this::toResponse));
    }

    @Transactional
    @Override
    public InvitationResponse provision(AccountProvisionRequest request, String adminUserId) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already exists");
        }

        String temporaryPassword = randomToken(32);

        UserEntity user = UserEntity.builder()
                .userId(UUID.randomUUID().toString())
                .name(request.getName())
                .email(request.getEmail())
                .password(passwordEncoder.encode(temporaryPassword))
                .isActive(false)
                .isAccountVerified(false)
                .build();
        UserEntity savedUser = userRepository.save(user);
        memberProfileRepository.save(MemberProfileEntity.create(savedUser));
        if (request.getRoleCodes() == null || request.getRoleCodes().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Select at least one active role");
        }
        String actorReference = resolveActorReference(adminUserId);
        List<String> assignedRoleCodes = assignRoles(savedUser, request.getRoleCodes(), actorReference);

        upsertInvite(savedUser.getEmail(), actorReference);
        AccountInvitationEntity invitation = accountInvitationRepository.findByEmail(savedUser.getEmail())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Invitation not found"));
        queueInvitationEmail(invitation, savedUser.getEmail());
        auditService.log(USER_PROVISIONED, USER, savedUser.getId().toString(), null,
                accountSnapshot(savedUser, assignedRoleCodes));
        return InvitationResponse.builder()
                .email(savedUser.getEmail())
                .status(InvitationStatus.PENDING)
                .expiresAt(invitation.getExpiresAt())
                .sentTo(savedUser.getEmail())
                .build();
    }

    @Transactional
    @Override
    public InvitationResponse resendInvite(String email, String adminUserId) {
        UserEntity user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + email));
        upsertInvite(user.getEmail(), resolveActorReference(adminUserId));
        AccountInvitationEntity invitation = accountInvitationRepository.findByEmail(user.getEmail())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Invitation not found"));
        queueInvitationEmail(invitation, user.getEmail());
        return InvitationResponse.builder()
                .email(invitation.getEmail())
                .status(invitation.getStatus())
                .expiresAt(invitation.getExpiresAt())
                .sentTo(invitation.getEmail())
                .build();
    }

    @Transactional(noRollbackFor = ResponseStatusException.class)
    @Override
    public AccountResponse acceptInvite(InvitationAcceptRequest request) {
        AccountInvitationEntity invitation = accountInvitationRepository.findByTokenHash(tokenHashService.sha256(request.getToken()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invitation token is invalid"));
        if (invitation.getExpiresAt().isBefore(Instant.now())) {
            invitation.setStatus(InvitationStatus.EXPIRED);
            accountInvitationRepository.save(invitation);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invitation expired");
        }
        if (invitation.getStatus() == InvitationStatus.ACCEPTED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invitation already accepted");
        }

        UserEntity user = userRepository.findByEmail(invitation.getEmail())
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + invitation.getEmail()));
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setIsActive(true);
        user.setIsAccountVerified(true);
        userRepository.save(user);
        MemberProfileEntity profile = memberProfileRepository.findByUserId(user.getId())
                .orElseGet(() -> MemberProfileEntity.create(user));
        profile.setActiveStatus("ACTIVE");
        memberProfileRepository.save(profile);

        invitation.setStatus(InvitationStatus.ACCEPTED);
        invitation.setAcceptedAt(Instant.now());
        accountInvitationRepository.save(invitation);
        outboxStateService.markActivated(invitation.getId());
        userSessionService.revokeAllByEmail(user.getEmail());
        return toResponse(user);
    }

    @Transactional
    @Override
    public AccountResponse updateRoles(String userId, Set<String> roleCodes, String adminUserId) {
        UserEntity user = getUserByUserId(userId);
        List<String> beforeCodes = userRoleRepository.findRolesByUserId(user.getId()).stream()
                .map(RoleEntity::getCode).sorted().toList();
        List<String> afterCodes = assignRoles(user, roleCodes, resolveActorReference(adminUserId));
        userSessionService.revokeAllByEmail(user.getEmail());
        auditService.log(USER_ROLES_UPDATED, USER, user.getId().toString(),
                Map.of("roleCodes", beforeCodes), Map.of("roleCodes", afterCodes));
        return toResponse(user);
    }

    @Transactional
    @Override
    public AccountResponse updateInformation(String userId, AccountUpdateRequest request) {
        UserEntity user = getUserByUserId(userId);
        String normalizedName = request.getName().trim().replaceAll("\\s+", " ");
        if (normalizedName.length() > 150) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Name must not exceed 150 characters");
        }
        String previousName = user.getName();
        if (!previousName.equals(normalizedName)) {
            user.setName(normalizedName);
            userRepository.save(user);
            auditService.log(USER_INFORMATION_UPDATED, USER, user.getId().toString(),
                    Map.of("name", previousName), Map.of("name", normalizedName));
        }
        return toResponse(user);
    }

    @Transactional
    @Override
    public AccountResponse setActive(String userId, boolean active) {
        UserEntity user = getUserByUserId(userId);
        MemberProfileEntity profile = memberProfileRepository.findByUserId(user.getId())
                .orElseGet(() -> MemberProfileEntity.create(user));
        Map<String, Object> before = accountStatusSnapshot(user, profile);
        user.setIsActive(active);
        userRepository.save(user);
        profile.setActiveStatus(active ? "ACTIVE" : "INACTIVE");
        memberProfileRepository.save(profile);
        if (!active) {
            userSessionService.revokeAllByEmail(user.getEmail());
        }
        Map<String, Object> after = accountStatusSnapshot(user, profile);
        if (!before.equals(after)) {
            auditService.log(USER_ACTIVE_STATUS_UPDATED, USER, user.getId().toString(), before, after);
        }
        return toResponse(user);
    }

    @Transactional
    @Override
    public AccountResponse setPermissionOverride(
            String userId,
            String permissionCode,
            PermissionOverrideRequest request,
            String adminUserId
    ) {
        UserEntity user = getUserByUserId(userId);
        String normalizedPermissionCode = permissionCode.trim().toUpperCase();
        PermissionEntity permission = permissionRepository.findByCode(normalizedPermissionCode)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Permission not found"));
        if (!Boolean.TRUE.equals(permission.getIsActive())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Inactive permissions cannot be overridden");
        }

        UserPermissionOverrideEntity override = userPermissionOverrideRepository
                .findByUserIdAndPermissionId(user.getId(), permission.getId())
                .orElseGet(() -> UserPermissionOverrideEntity.builder()
                        .user(user)
                        .permission(permission)
                        .build());
        Map<String, Object> before = overrideSnapshot(permission.getCode(), override.getEffect());
        override.setEffect(request.getEffect());
        override.setChangedBy(resolveActorReference(adminUserId));
        userPermissionOverrideRepository.save(override);
        userSessionService.revokeAllByEmail(user.getEmail());
        auditService.log(USER_PERMISSION_OVERRIDE_SET, USER, user.getId().toString(), before,
                overrideSnapshot(permission.getCode(), override.getEffect()));
        return toResponse(user);
    }

    @Transactional
    @Override
    public AccountResponse removePermissionOverride(String userId, String permissionCode) {
        UserEntity user = getUserByUserId(userId);
        PermissionEntity permission = permissionRepository.findByCode(permissionCode.trim().toUpperCase())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Permission not found"));
        userPermissionOverrideRepository.findByUserIdAndPermissionId(user.getId(), permission.getId())
                .ifPresent(existing -> {
                    userPermissionOverrideRepository.deleteByUserIdAndPermissionId(user.getId(), permission.getId());
                    auditService.log(USER_PERMISSION_OVERRIDE_REMOVED, USER, user.getId().toString(),
                            overrideSnapshot(permission.getCode(), existing.getEffect()), null);
                });
        userSessionService.revokeAllByEmail(user.getEmail());
        return toResponse(user);
    }

    @Transactional(readOnly = true)
    @Override
    public AccountResponse toResponse(UserEntity user) {
        return AccountResponse.builder()
                .userId(user.getUserId())
                .name(user.getName())
                .email(user.getEmail())
                .isActive(user.getIsActive())
                .isAccountVerified(user.getIsAccountVerified())
                .roles(permissionService.getRoleCodes(user))
                .permissions(permissionService.getEffectivePermissionCodes(user))
                .permissionOverrides(userPermissionOverrideRepository.findByUserId(user.getId()).stream()
                        .map(override -> PermissionOverrideResponse.builder()
                                .permissionCode(override.getPermission().getCode())
                                .effect(override.getEffect())
                                .build())
                        .sorted(java.util.Comparator.comparing(PermissionOverrideResponse::getPermissionCode))
                        .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new)))
                .build();
    }

    private List<String> assignRoles(UserEntity user, Set<String> roleCodes, String adminUserId) {
        Set<String> normalizedCodes = new HashSet<>();
        for (String roleCode : roleCodes) {
            normalizedCodes.add(roleCode.trim().toUpperCase());
        }
        Set<RoleEntity> roles = new HashSet<>(roleRepository.findByCodeIn(normalizedCodes));
        if (roles.size() != normalizedCodes.size() || roles.stream().anyMatch(role -> !Boolean.TRUE.equals(role.getIsActive()))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "One or more role codes are invalid or inactive");
        }

        userRoleRepository.deleteByUserId(user.getId());
        for (RoleEntity role : roles) {
            userRoleRepository.save(UserRoleEntity.builder()
                    .user(user)
                    .role(role)
                    .assignedBy(adminUserId)
                    .build());
        }
        return roles.stream().map(RoleEntity::getCode).sorted().toList();
    }

    private void upsertInvite(String email, String adminUserId) {
        AccountInvitationEntity invitation = accountInvitationRepository.findByEmail(email)
                .orElseGet(() -> AccountInvitationEntity.builder()
                        .invitationId(UUID.randomUUID().toString())
                        .email(email)
                        .resendCount(0)
                        .build());
        // This placeholder is replaced only by the outbox worker immediately before delivery.
        invitation.setTokenHash(tokenHashService.sha256(randomToken(48)));
        invitation.setStatus(InvitationStatus.PENDING);
        invitation.setExpiresAt(Instant.now().plus(inviteTtlHours, ChronoUnit.HOURS));
        invitation.setAcceptedAt(null);
        invitation.setInvitedBy(adminUserId);
        invitation.setResendCount(invitation.getResendCount() == null ? 0 : invitation.getResendCount() + 1);
        accountInvitationRepository.save(invitation);
    }

    private void queueInvitationEmail(AccountInvitationEntity invitation, String email) {
        emailOutboxRepository.save(EmailOutboxEntity.builder()
                .messageId(UUID.randomUUID().toString())
                .templateCode("ACCOUNT_INVITATION")
                .recipientEmail(email)
                .invitation(invitation)
                .status(EmailOutboxStatus.QUEUED)
                .attemptCount(0)
                .nextAttemptAt(Instant.now())
                .build());
    }

    private UserEntity getUserByUserId(String userId) {
        return userRepository.findByUserId(userId)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + userId));
    }

    private String randomToken(int byteLength) {
        byte[] bytes = new byte[byteLength];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static Map<String, Object> overrideSnapshot(String permissionCode, Object effect) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("permissionCode", permissionCode);
        snapshot.put("effect", effect == null ? null : effect.toString());
        return snapshot;
    }

    private String resolveActorReference(String principal) {
        if (principal == null || principal.isBlank()) {
            return null;
        }
        return userRepository.findByEmail(principal)
                .map(UserEntity::getUserId)
                .orElseGet(() -> principal.length() <= 36 ? principal : null);
    }

    private static Map<String, Object> accountSnapshot(UserEntity user, List<String> roleCodes) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("userId", user.getUserId());
        snapshot.put("name", user.getName());
        snapshot.put("email", user.getEmail());
        snapshot.put("isActive", user.getIsActive());
        snapshot.put("isAccountVerified", user.getIsAccountVerified());
        snapshot.put("roleCodes", roleCodes);
        return snapshot;
    }

    private static Map<String, Object> accountStatusSnapshot(UserEntity user, MemberProfileEntity profile) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("isActive", user.getIsActive());
        snapshot.put("activeStatus", profile.getActiveStatus());
        return snapshot;
    }
}
