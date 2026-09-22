package com.smartlab.service.impl;

import com.smartlab.entity.UserEntity;
import com.smartlab.repo.UserRepository;
import com.smartlab.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.time.Instant;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@ExtendWith(MockitoExtension.class)
class PasswordResetServiceImplTest {
    private static final String EMAIL = "member@example.test";
    @Mock UserRepository users;
    @Mock PasswordEncoder encoder;
    @Mock EmailService emails;
    @Mock PermissionService permissions;
    @Mock UserSessionService sessions;
    @InjectMocks PasswordResetServiceImpl service;
    private UserEntity user;

    @BeforeEach void setup() {
        user = UserEntity.builder().id(1L).email(EMAIL).password("old-hash")
                .isActive(true).isAccountVerified(true).resetOtp("$2a$otp-hash")
                .resetOtpExpireAt(Instant.now().plusSeconds(600)).build();
    }

    private void existing() { when(users.findByEmailForUpdate(EMAIL)).thenReturn(Optional.of(user)); }

    @Test void sendStoresHashAndEnforcesCooldownWithoutInvalidatingCurrentCode() {
        existing();
        when(encoder.encode(anyString())).thenReturn("$2a$new-hash");
        service.sendResetOtp(" " + EMAIL + " ");
        ArgumentCaptor<String> code = ArgumentCaptor.forClass(String.class);
        verify(emails).sendResetOtpEmail(eq(EMAIL), code.capture());
        assertThat(code.getValue()).matches("[0-9]{6}");
        assertThat(user.getResetOtp()).isEqualTo("$2a$new-hash").isNotEqualTo(code.getValue());
        service.sendResetOtp(EMAIL);
        verify(emails, times(1)).sendResetOtpEmail(anyString(), anyString());
    }

    @Test void missingAccountGetsNoEmailAndNoException() {
        service.sendResetOtp(EMAIL);
        verifyNoInteractions(emails, encoder, sessions);
    }

    @Test void inactiveOrUnverifiedAccountsCannotRequestOrUseRecovery() {
        existing();
        user.setIsAccountVerified(false);
        service.sendResetOtp(EMAIL);
        assertThatThrownBy(() -> service.resetPassword(EMAIL, "123456", "valid-password"))
                .isInstanceOf(PasswordResetServiceImpl.ResetOtpRejectedException.class);
        user.setIsAccountVerified(true);
        user.setIsActive(false);
        service.sendResetOtp(EMAIL);
        assertThatThrownBy(() -> service.verifyResetOtp(EMAIL, "123456"))
                .isInstanceOf(PasswordResetServiceImpl.ResetOtpRejectedException.class);
        verifyNoInteractions(emails, encoder, sessions);
    }

    @Test void disabledRoleBlocksRecovery() {
        existing();
        when(permissions.hasInactiveAssignedRole(user)).thenReturn(true);
        service.sendResetOtp(EMAIL);
        assertThatThrownBy(() -> service.resetPassword(EMAIL, "123456", "valid-password"))
                .isInstanceOf(PasswordResetServiceImpl.ResetOtpRejectedException.class);
        verifyNoInteractions(emails, encoder, sessions);
    }

    @Test void smtpFailureDoesNotExposeDetailsOrReplaceExistingChallenge() {
        existing();
        when(encoder.encode(anyString())).thenReturn("$2a$new");
        doThrow(new IllegalStateException("private-smtp-detail")).when(emails).sendResetOtpEmail(anyString(), anyString());
        assertThatCode(() -> service.sendResetOtp(EMAIL)).doesNotThrowAnyException();
        assertThat(user.getResetOtp()).isEqualTo("$2a$otp-hash");
        verify(users, never()).save(any());
    }

    @Test void verificationDoesNotConsumeCodeButDoesNotAuthorizeResetWithWrongCode() {
        existing();
        when(encoder.matches("123456", user.getResetOtp())).thenReturn(true);
        service.verifyResetOtp(EMAIL, "123456");
        assertThat(user.getResetOtp()).isNotNull();
        assertThatThrownBy(() -> service.resetPassword(EMAIL, "654321", "valid-password"))
                .isInstanceOf(PasswordResetServiceImpl.ResetOtpRejectedException.class);
        assertThat(user.getPassword()).isEqualTo("old-hash");
        verifyNoInteractions(sessions);
    }

    @Test void wrongAttemptsAreSharedBetweenVerifyAndResetAndExhaustCode() {
        existing();
        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> service.verifyResetOtp(EMAIL, "000000"))
                    .isInstanceOf(PasswordResetServiceImpl.ResetOtpRejectedException.class);
        }
        assertThat(user.getResetOtpFailedAttempts()).isEqualTo(5);
        assertThat(user.getResetOtp()).isNull();
        assertThatThrownBy(() -> service.resetPassword(EMAIL, "123456", "valid-password"))
                .isInstanceOf(PasswordResetServiceImpl.ResetOtpRejectedException.class);
        verifyNoInteractions(sessions);
    }

    @Test void expiredAndLegacyPlaintextCodesAreRejected() {
        existing();
        user.setResetOtpExpireAt(Instant.now().minusSeconds(1));
        assertThatThrownBy(() -> service.verifyResetOtp(EMAIL, "123456"))
                .isInstanceOf(PasswordResetServiceImpl.ResetOtpRejectedException.class);
        user.setResetOtpExpireAt(Instant.now().plusSeconds(30));
        user.setResetOtp("123456");
        assertThatThrownBy(() -> service.verifyResetOtp(EMAIL, "123456"))
                .isInstanceOf(PasswordResetServiceImpl.ResetOtpRejectedException.class);
        verifyNoInteractions(encoder, sessions);
    }

    @Test void validResetConsumesCodeAndRevokesUsingCanonicalEmail() {
        existing();
        when(encoder.matches("123456", user.getResetOtp())).thenReturn(true);
        when(encoder.encode("valid-password")).thenReturn("new-password-hash");
        service.resetPassword(" " + EMAIL + " ", "123456", "valid-password");
        assertThat(user.getPassword()).isEqualTo("new-password-hash");
        assertThat(user.getResetOtp()).isNull();
        verify(sessions).revokeAllByEmail(EMAIL);
        assertThatThrownBy(() -> service.resetPassword(EMAIL, "123456", "another-password"))
                .isInstanceOf(PasswordResetServiceImpl.ResetOtpRejectedException.class);
        verify(sessions, times(1)).revokeAllByEmail(EMAIL);
    }

    @Test void shortBlankOrOversizedUtf8PasswordsDoNotConsumeOtp() {
        for (String password : new String[]{"12345", "      ", "a".repeat(73), "ấ".repeat(25)}) {
            assertThatThrownBy(() -> service.resetPassword(EMAIL, "123456", password))
                    .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        }
        verifyNoInteractions(users, sessions);
    }
}
