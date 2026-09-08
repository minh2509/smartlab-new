package com.smartlab.service.impl;

import com.smartlab.dto.request.BulkAccountInvitationRequest;
import com.smartlab.dto.request.BulkAccountInvitationRowRequest;
import com.smartlab.dto.response.BulkAccountInvitationPreviewResponse;
import com.smartlab.entity.RoleEntity;
import com.smartlab.enums.BulkInvitationItemStatus;
import com.smartlab.repo.*;
import com.smartlab.service.AuditService;
import com.smartlab.service.TokenHashService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BulkAccountInvitationServiceImplTest {
    @Mock private AccountInvitationBatchRepository batches;
    @Mock private AccountInvitationBatchRoleRepository batchRoles;
    @Mock private AccountInvitationItemRepository items;
    @Mock private AccountInvitationRepository invitations;
    @Mock private EmailOutboxRepository outbox;
    @Mock private UserRepository users;
    @Mock private MemberProfileRepository memberProfiles;
    @Mock private RoleRepository roles;
    @Mock private UserRoleRepository userRoles;
    @Mock private TokenHashService tokenHashes;
    @Mock private org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;
    @Mock private AuditService audit;

    private BulkAccountInvitationServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new BulkAccountInvitationServiceImpl(batches, batchRoles, items, invitations, outbox, users,
                memberProfiles, roles, userRoles, tokenHashes, passwordEncoder, audit);
    }

    @Test
    void previewNormalizesAndExplainsMixedRowsWithoutCreatingAnything() {
        when(roles.findByCodeIn(Set.of("MEMBER"))).thenReturn(List.of(RoleEntity.builder().id(2L).code("MEMBER").isActive(true).build()));
        when(users.existsByEmail(anyString())).thenAnswer(invocation ->
                "existing@example.test".equals(invocation.getArgument(0)));

        BulkAccountInvitationPreviewResponse preview = service.preview(request(
                row("  Nguyen Van A  ", " NGUYEN@example.test "),
                row("Duplicate", "nguyen@example.test"),
                row("", "missing-name@example.test"),
                row("Bad email", "not-an-email"),
                row("Existing", "existing@example.test")
        ));

        assertThat(preview.getRequestedCount()).isEqualTo(5);
        assertThat(preview.getAcceptedCount()).isEqualTo(1);
        assertThat(preview.getRejectedCount()).isEqualTo(4);
        assertThat(preview.getItems()).extracting(item -> item.getStatus())
                .containsExactly(BulkInvitationItemStatus.VALID.name(), BulkInvitationItemStatus.REJECTED_DUPLICATE.name(),
                        BulkInvitationItemStatus.REJECTED_INVALID.name(), BulkInvitationItemStatus.REJECTED_INVALID.name(),
                        BulkInvitationItemStatus.REJECTED_ALREADY_EXISTS.name());
        assertThat(preview.getItems().getFirst().getEmail()).isEqualTo("nguyen@example.test");
    }

    private static BulkAccountInvitationRequest request(BulkAccountInvitationRowRequest... rows) {
        BulkAccountInvitationRequest request = new BulkAccountInvitationRequest();
        request.setRoleCodes(Set.of("member"));
        request.setItems(List.of(rows));
        return request;
    }

    private static BulkAccountInvitationRowRequest row(String fullName, String email) {
        BulkAccountInvitationRowRequest row = new BulkAccountInvitationRowRequest();
        row.setFullName(fullName);
        row.setEmail(email);
        return row;
    }
}
