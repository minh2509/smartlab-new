package com.smartlab.service.impl;

import com.smartlab.entity.UserEntity;
import com.smartlab.repo.UserRepository;
import com.smartlab.service.EmailService;
import com.smartlab.service.PasswordResetService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor
public class PasswordResetServiceImpl implements PasswordResetService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;

    @Override
    public void sendResetOtp(String email) {
        UserEntity existingUser = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + email));
        String otp = String.valueOf(ThreadLocalRandom.current().nextInt(100000, 999999));
        Instant expiryTime = Instant.now().plus(Duration.ofMinutes(15));

        existingUser.setResetOtp(otp);
        existingUser.setResetOtpExpireAt(expiryTime);
        userRepository.save(existingUser);

        try {
            emailService.sendResetOtpEmail(existingUser.getEmail(), otp);
        } catch (Exception e) {
            throw new RuntimeException("Unable to send reset OTP");
        }
    }

    @Override
    public void verifyResetOtp(String email, String otp) {
        UserEntity existingUser = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + email));
        validateResetOtp(existingUser, otp);
    }

    @Override
    public void resetPassword(String email, String otp, String newPassword) {
        UserEntity existingUser = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + email));
        validateResetOtp(existingUser, otp);

        existingUser.setPassword(passwordEncoder.encode(newPassword));
        existingUser.setResetOtp(null);
        existingUser.setResetOtpExpireAt(null);
        userRepository.save(existingUser);
    }

    private void validateResetOtp(UserEntity existingUser, String otp) {
        if (existingUser.getResetOtp() == null || !existingUser.getResetOtp().equals(otp)) {
            throw new RuntimeException("Reset OTP is not valid");
        }
        if (existingUser.getResetOtpExpireAt() == null || Instant.now().isAfter(existingUser.getResetOtpExpireAt())) {
            throw new RuntimeException("Reset OTP expired");
        }
    }
}
