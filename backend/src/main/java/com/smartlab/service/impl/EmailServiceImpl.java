package com.smartlab.service.impl;

import com.smartlab.service.EmailService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EmailServiceImpl implements EmailService {
    private final JavaMailSender mailSender;
    @Value("${spring.mail.properties.mail.smtp.from}")
    private String fromEmail;

    @Override
    public void sendResetOtpEmail(String toEmail, String otp) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromEmail);
        message.setTo(toEmail);
        message.setSubject("Reset OTP");
        message.setText("Your otp for resetting your password is: " + otp + ". Use this otp to reset your password.");
        mailSender.send(message);

    }

    @Override
    public void sendInvitationEmail(String toEmail, String invitationLink, String expiresAt) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromEmail);
        message.setTo(toEmail);
        message.setSubject("Smart Lab account invitation");
        message.setText("""
                You have been invited to Smart Lab.

                Open this link to activate your account and set your password:
                %s

                This invitation expires at: %s
                """.formatted(invitationLink, expiresAt));
        mailSender.send(message);
    }
}
