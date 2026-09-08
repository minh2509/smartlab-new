package com.smartlab.service;

import java.time.Instant;

public interface EmailService {
    void sendResetOtpEmail(String toEmail, String otp);

    void sendInvitationEmail(String toEmail, String fullName, String invitationLink, Instant expiresAt);
}
