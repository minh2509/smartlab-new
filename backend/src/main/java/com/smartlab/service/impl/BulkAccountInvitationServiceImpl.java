package com.smartlab.service.impl;

import com.smartlab.dto.request.BulkAccountInvitationRequest;
import com.smartlab.dto.request.BulkAccountInvitationRowRequest;
import com.smartlab.dto.response.BulkAccountInvitationBatchResponse;
import com.smartlab.dto.response.BulkAccountInvitationItemResponse;
import com.smartlab.dto.response.BulkAccountInvitationPreviewResponse;
import com.smartlab.entity.*;
import com.smartlab.enums.BulkInvitationBatchStatus;
import com.smartlab.enums.BulkInvitationItemStatus;
import com.smartlab.enums.EmailOutboxStatus;
import com.smartlab.enums.InvitationStatus;
import com.smartlab.repo.*;
import com.smartlab.service.AuditService;
import com.smartlab.service.BulkAccountInvitationService;
import com.smartlab.service.TokenHashService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static com.smartlab.service.AuditVocabulary.ACCOUNT_INVITATION_BATCH;
import static com.smartlab.service.AuditVocabulary.ACCOUNT_INVITATION_BATCH_CREATED;
import static com.smartlab.service.AuditVocabulary.ACCOUNT_INVITATION_ITEM_RESENT;

/** Bulk provisioning for SmartLab. SMTP delivery is intentionally delegated to the database outbox. */
@Service
@RequiredArgsConstructor
public class BulkAccountInvitationServiceImpl implements BulkAccountInvitationService {
    private static final String INVITATION_TEMPLATE = "ACCOUNT_INVITATION";

    private final AccountInvitationBatchRepository batchRepository;
    private final AccountInvitationBatchRoleRepository batchRoleRepository;
    private final AccountInvitationItemRepository itemRepository;
    private final AccountInvitationRepository invitationRepository;
    private final EmailOutboxRepository emailOutboxRepository;
    private final UserRepository userRepository;
    private final MemberProfileRepository memberProfileRepository;
    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;
    private final TokenHashService tokenHashService;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${smartlab.invite.ttl-hours:72}")
    private long invitationTtlHours;

    @Transactional(readOnly = true)
    @Override
    public BulkAccountInvitationPreviewResponse preview(BulkAccountInvitationRequest request) {
        validateRequestAndRoles(request);
        return toPreview(validateRows(request.getItems()));
    }

    @Transactional
    @Override
    public BulkAccountInvitationBatchResponse provision(BulkAccountInvitationRequest request, String adminEmail) {
        List<RoleEntity> roles = validateRequestAndRoles(request);
        List<ValidatedRow> rows = validateRows(request.getItems());
        long acceptedCount = rows.stream().filter(ValidatedRow::accepted).count();
        if (acceptedCount == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No valid, new account row is available to provision");
        }

        AccountInvitationBatchEntity batch = batchRepository.save(AccountInvitationBatchEntity.builder()
                .batchId(UUID.randomUUID().toString())
                .createdBy(normalizeRequired(adminEmail, "Authenticated administrator is required"))
                .status(BulkInvitationBatchStatus.QUEUED)
                .requestedCount(rows.size())
                .acceptedCount((int) acceptedCount)
                .rejectedCount((int) (rows.size() - acceptedCount))
                .build());

        for (RoleEntity role : roles) {
            batchRoleRepository.save(AccountInvitationBatchRoleEntity.builder().batch(batch).role(role).build());
        }

        for (ValidatedRow row : rows) {
            AccountInvitationItemEntity item = AccountInvitationItemEntity.builder()
                    .itemId(UUID.randomUUID().toString())
                    .batch(batch)
                    .sourceRow(row.sourceRow())
                    .fullName(row.fullName())
                    .email(row.email())
                    .status(row.status())
                    .failureCode(row.failureCode())
                    .failureMessage(row.failureMessage())
                    .resendCount(0)
                    .build();
            if (row.accepted()) provisionRow(item, roles, adminEmail);
            itemRepository.save(item);
            if (row.accepted()) queueEmail(item.getInvitation(), item);
        }

        auditService.log(ACCOUNT_INVITATION_BATCH_CREATED, ACCOUNT_INVITATION_BATCH, batch.getBatchId(), null,
                Map.of("requestedCount", batch.getRequestedCount(), "acceptedCount", batch.getAcceptedCount(),
                        "rejectedCount", batch.getRejectedCount(), "roleCodes", roles.stream().map(RoleEntity::getCode).sorted().toList()));
        return toBatchResponse(batch);
    }

    @Transactional(readOnly = true)
    @Override
    public BulkAccountInvitationBatchResponse getBatch(String batchId) {
        return toBatchResponse(findBatch(batchId));
    }

    @Transactional
    @Override
    public void resend(String batchId, String itemId, String adminEmail) {
        AccountInvitationItemEntity item = itemRepository.findByItemIdAndBatchBatchId(itemId, batchId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Invitation item not found"));
        if (item.getStatus() == BulkInvitationItemStatus.ACTIVATED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "An activated account cannot receive an invitation resend");
        }
        if (item.getInvitation() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "This row does not have a provisioned account");
        }
        item.setStatus(BulkInvitationItemStatus.QUEUED);
        item.setFailureCode(null);
        item.setFailureMessage(null);
        item.setResendCount(item.getResendCount() + 1);
        queueEmail(item.getInvitation(), item);
        auditService.log(ACCOUNT_INVITATION_ITEM_RESENT, ACCOUNT_INVITATION_BATCH, batchId, null,
                Map.of("itemId", itemId, "email", item.getEmail(), "requestedBy", normalizeRequired(adminEmail, "Authenticated administrator is required")));
    }

    private void provisionRow(AccountInvitationItemEntity item, List<RoleEntity> roles, String adminEmail) {
        UserEntity user = userRepository.save(UserEntity.builder()
                .userId(UUID.randomUUID().toString())
                .name(item.getFullName())
                .email(item.getEmail())
                .password(passwordEncoder.encode(randomToken(32)))
                .isActive(false)
                .isAccountVerified(false)
                .build());
        memberProfileRepository.save(MemberProfileEntity.create(user));
        for (RoleEntity role : roles) {
            userRoleRepository.save(UserRoleEntity.builder().user(user).role(role).assignedBy(adminEmail).build());
        }

        AccountInvitationEntity invitation = invitationRepository.save(AccountInvitationEntity.builder()
                .invitationId(UUID.randomUUID().toString())
                .email(user.getEmail())
                // This placeholder hash is replaced immediately before SMTP delivery. Raw tokens are never persisted.
                .tokenHash(tokenHashService.sha256(randomToken(48)))
                .status(InvitationStatus.PENDING)
                .expiresAt(Instant.now().plus(invitationTtlHours, ChronoUnit.HOURS))
                .invitedBy(adminEmail)
                .resendCount(0)
                .build());
        item.setUser(user);
        item.setInvitation(invitation);
        item.setStatus(BulkInvitationItemStatus.QUEUED);
    }

    private void queueEmail(AccountInvitationEntity invitation, AccountInvitationItemEntity item) {
        emailOutboxRepository.save(EmailOutboxEntity.builder()
                .messageId(UUID.randomUUID().toString())
                .templateCode(INVITATION_TEMPLATE)
                .recipientEmail(item.getEmail())
                .invitation(invitation)
                .batchItem(item)
                .status(EmailOutboxStatus.QUEUED)
                .attemptCount(0)
                .nextAttemptAt(Instant.now())
                .build());
    }

    private List<RoleEntity> validateRequestAndRoles(BulkAccountInvitationRequest request) {
        if (request == null || request.getItems() == null || request.getItems().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Provide at least one account row");
        }
        if (request.getItems().size() > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A batch may contain at most 100 account rows");
        }
        Set<String> roleCodes = new TreeSet<>();
        if (request.getRoleCodes() != null) {
            for (String code : request.getRoleCodes()) {
                String normalized = normalize(code);
                if (!normalized.isBlank()) roleCodes.add(normalized.toUpperCase(Locale.ROOT));
            }
        }
        if (roleCodes.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Select at least one active role");
        List<RoleEntity> roles = roleRepository.findByCodeIn(roleCodes);
        if (roles.size() != roleCodes.size() || roles.stream().anyMatch(role -> !Boolean.TRUE.equals(role.getIsActive()))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "One or more selected roles are missing or inactive");
        }
        return roles;
    }

    private List<ValidatedRow> validateRows(List<BulkAccountInvitationRowRequest> requestedRows) {
        Set<String> seenEmails = new HashSet<>();
        List<ValidatedRow> rows = new ArrayList<>();
        for (int index = 0; index < requestedRows.size(); index++) {
            BulkAccountInvitationRowRequest row = requestedRows.get(index);
            String fullName = normalize(row == null ? null : row.getFullName());
            String email = normalize(row == null ? null : row.getEmail()).toLowerCase(Locale.ROOT);
            int sourceRow = index + 1;
            if (fullName.isBlank()) {
                rows.add(ValidatedRow.rejected(sourceRow, fullName, email, BulkInvitationItemStatus.REJECTED_INVALID, "FULL_NAME_REQUIRED", "Full name is required"));
            } else if (!isEmail(email)) {
                rows.add(ValidatedRow.rejected(sourceRow, fullName, email, BulkInvitationItemStatus.REJECTED_INVALID, "INVALID_EMAIL", "Email address is invalid"));
            } else if (!seenEmails.add(email)) {
                rows.add(ValidatedRow.rejected(sourceRow, fullName, email, BulkInvitationItemStatus.REJECTED_DUPLICATE, "DUPLICATE_IN_BATCH", "Email is duplicated in this batch"));
            } else if (Boolean.TRUE.equals(userRepository.existsByEmail(email))) {
                rows.add(ValidatedRow.rejected(sourceRow, fullName, email, BulkInvitationItemStatus.REJECTED_ALREADY_EXISTS, "ACCOUNT_ALREADY_EXISTS", "An account already uses this email"));
            } else {
                rows.add(ValidatedRow.accepted(sourceRow, fullName, email));
            }
        }
        return rows;
    }

    private BulkAccountInvitationPreviewResponse toPreview(List<ValidatedRow> rows) {
        int accepted = (int) rows.stream().filter(ValidatedRow::accepted).count();
        return BulkAccountInvitationPreviewResponse.builder()
                .requestedCount(rows.size()).acceptedCount(accepted).rejectedCount(rows.size() - accepted)
                .items(rows.stream().map(this::toResponse).toList()).build();
    }

    private BulkAccountInvitationBatchResponse toBatchResponse(AccountInvitationBatchEntity batch) {
        return BulkAccountInvitationBatchResponse.builder()
                .batchId(batch.getBatchId()).status(batch.getStatus().name())
                .requestedCount(batch.getRequestedCount()).acceptedCount(batch.getAcceptedCount()).rejectedCount(batch.getRejectedCount())
                .createdBy(batch.getCreatedBy()).createdAt(batch.getCreatedAt()).completedAt(batch.getCompletedAt())
                .roleCodes(batchRoleRepository.findByBatchBatchIdOrderByRoleCodeAsc(batch.getBatchId()).stream().map(link -> link.getRole().getCode()).toList())
                .items(itemRepository.findByBatchBatchIdOrderBySourceRowAsc(batch.getBatchId()).stream().map(this::toResponse).toList())
                .build();
    }

    private BulkAccountInvitationItemResponse toResponse(ValidatedRow row) {
        return BulkAccountInvitationItemResponse.builder().sourceRow(row.sourceRow()).fullName(row.fullName()).email(row.email())
                .status(row.status().name()).failureCode(row.failureCode()).failureMessage(row.failureMessage()).resendCount(0).build();
    }

    private BulkAccountInvitationItemResponse toResponse(AccountInvitationItemEntity item) {
        return BulkAccountInvitationItemResponse.builder().sourceRow(item.getSourceRow()).itemId(item.getItemId()).fullName(item.getFullName())
                .email(item.getEmail()).status(item.getStatus().name()).failureCode(item.getFailureCode())
                .failureMessage(item.getFailureMessage()).resendCount(item.getResendCount()).build();
    }

    private AccountInvitationBatchEntity findBatch(String batchId) {
        return batchRepository.findByBatchId(batchId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Invitation batch not found"));
    }

    private String randomToken(int byteLength) {
        byte[] bytes = new byte[byteLength];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String normalize(String value) { return value == null ? "" : value.trim(); }
    private static String normalizeRequired(String value, String message) {
        String normalized = normalize(value);
        if (normalized.isBlank()) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, message);
        return normalized;
    }
    private static boolean isEmail(String value) { return value.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$"); }

    private record ValidatedRow(int sourceRow, String fullName, String email, BulkInvitationItemStatus status,
                                String failureCode, String failureMessage) {
        static ValidatedRow accepted(int sourceRow, String fullName, String email) {
            return new ValidatedRow(sourceRow, fullName, email, BulkInvitationItemStatus.VALID, null, null);
        }
        static ValidatedRow rejected(int sourceRow, String fullName, String email, BulkInvitationItemStatus status, String code, String message) {
            return new ValidatedRow(sourceRow, fullName, email, status, code, message);
        }
        boolean accepted() { return status == BulkInvitationItemStatus.VALID; }
    }
}
