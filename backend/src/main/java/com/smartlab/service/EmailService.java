package com.smartlab.service;

public interface EmailService {
    void sendResetOtpEmail(String toEmail, String otp);

    void sendInvitationEmail(String toEmail, String invitationLink, String expiresAt);
}
