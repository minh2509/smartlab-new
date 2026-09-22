package com.smartlab.service.impl;

import com.smartlab.entity.UserEntity;
import com.smartlab.repo.UserRepository;
import com.smartlab.service.EmailService;
import com.smartlab.service.PasswordPolicy;
import com.smartlab.service.PasswordResetService;
import com.smartlab.service.PermissionService;
import com.smartlab.service.UserSessionService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class PasswordResetServiceImpl implements PasswordResetService {
    private static final Logger LOG = LoggerFactory.getLogger(PasswordResetServiceImpl.class);
    private static final int MAX_ATTEMPTS = 5;
    private static final int TTL_SECONDS = 15 * 60;
    private final SecureRandom random = new SecureRandom();
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final PermissionService permissionService;
    private final UserSessionService userSessionService;

    @Override
    @Transactional
    public void sendResetOtp(String email) {
        UserEntity user = userRepository.findByEmailForUpdate(normalizeEmail(email)).orElse(null);
        if (!isEligible(user)) return;
        Instant now = Instant.now();
        // Persisted cooldown covers parallel requests and multiple app instances.
        if (user.getResetOtpRequestedAt() != null
                && now.isBefore(user.getResetOtpRequestedAt().plusSeconds(60))) return;
        String otp = String.format(Locale.ROOT, "%06d", random.nextInt(1_000_000));
        String hash = passwordEncoder.encode(otp);
        try {
            emailService.sendResetOtpEmail(user.getEmail(), otp);
        } catch (Exception failure) {
            // Do not identify registered accounts or expose SMTP details.
            LOG.warn("Password recovery email delivery failed");
            return;
        }
        user.setResetOtp(hash);
        user.setResetOtpExpireAt(now.plusSeconds(TTL_SECONDS));
        user.setResetOtpRequestedAt(now);
        user.setResetOtpFailedAttempts(0);
        userRepository.save(user);
    }

    @Override
    @Transactional(noRollbackFor = ResetOtpRejectedException.class)
    public void verifyResetOtp(String email, String otp) {
        validateResetOtp(requireEligibleUser(email), otp);
    }

    @Override
    @Transactional(noRollbackFor = ResetOtpRejectedException.class)
    public void resetPassword(String email, String otp, String newPassword) {
        PasswordPolicy.validate(newPassword);
        UserEntity user = requireEligibleUser(email);
        // Revalidate here; completing the UI verification step is not authorization.
        validateResetOtp(user, otp);
        user.setPassword(passwordEncoder.encode(newPassword));
        user.setResetOtp(null);
        user.setResetOtpExpireAt(null);
        user.setResetOtpFailedAttempts(0);
        user.setResetOtpRequestedAt(null);
        userRepository.save(user);
        // A revocation failure rolls back both password and token consumption.
        userSessionService.revokeAllByEmail(user.getEmail());
    }

    private UserEntity requireEligibleUser(String email) {
        UserEntity user = userRepository.findByEmailForUpdate(normalizeEmail(email)).orElse(null);
        if (!isEligible(user)) throw new ResetOtpRejectedException();
        return user;
    }

    private boolean isEligible(UserEntity user) {
        return user != null && Boolean.TRUE.equals(user.getIsActive())
                && Boolean.TRUE.equals(user.getIsAccountVerified())
                && !permissionService.hasInactiveAssignedRole(user);
    }

    private void validateResetOtp(UserEntity user, String otp) {
        int attempts = user.getResetOtpFailedAttempts() == null ? 0 : user.getResetOtpFailedAttempts();
        if (user.getResetOtp() == null || user.getResetOtpExpireAt() == null
                || !Instant.now().isBefore(user.getResetOtpExpireAt()) || attempts >= MAX_ATTEMPTS) {
            throw new ResetOtpRejectedException();
        }
        // Legacy plaintext OTPs are not accepted.
        if (otp == null || !otp.matches("[0-9]{6}") || !user.getResetOtp().startsWith("$2")
                || !passwordEncoder.matches(otp, user.getResetOtp())) {
            user.setResetOtpFailedAttempts(attempts + 1);
            if (attempts + 1 >= MAX_ATTEMPTS) user.setResetOtp(null);
            userRepository.save(user);
            throw new ResetOtpRejectedException();
        }
    }

    private String normalizeEmail(String email) {
        return email == null ? "" : email.trim();
    }

    public static class ResetOtpRejectedException extends ResponseStatusException {
        public ResetOtpRejectedException() {
            super(HttpStatus.BAD_REQUEST, "Mã OTP không hợp lệ, đã hết hạn hoặc không còn sử dụng được. Vui lòng yêu cầu mã mới.");
        }
    }
}
