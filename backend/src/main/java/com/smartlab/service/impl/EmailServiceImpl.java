package com.smartlab.service.impl;

import com.smartlab.entity.EmailTemplateEntity;
import com.smartlab.repo.EmailTemplateRepository;
import com.smartlab.service.EmailService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class EmailServiceImpl implements EmailService {
    private static final String INVITATION_TEMPLATE = "ACCOUNT_INVITATION";
    private static final String PASSWORD_RESET_TEMPLATE = "PASSWORD_RESET_OTP";
    private static final DateTimeFormatter VIETNAM_DATE_TIME = DateTimeFormatter
            .ofPattern("'lúc' HH:mm 'ngày' dd/MM/yyyy '(GMT+7)'", Locale.forLanguageTag("vi-VN"))
            .withZone(ZoneId.of("Asia/Ho_Chi_Minh"));

    private final JavaMailSender mailSender;
    private final EmailTemplateRepository emailTemplateRepository;
    @Value("${spring.mail.properties.mail.smtp.from}")
    private String fromEmail;
    @Value("${smartlab.email.from-name:SmartLab}")
    private String fromName;

    @Override
    public void sendResetOtpEmail(String toEmail, String otp) {
        sendTemplate(toEmail, PASSWORD_RESET_TEMPLATE, Map.of("otp", otp));
    }

    @Override
    public void sendInvitationEmail(String toEmail, String fullName, String invitationLink, Instant expiresAt) {
        sendTemplate(toEmail, INVITATION_TEMPLATE, Map.of(
                "fullName", greetingName(fullName),
                "inviteLink", invitationLink,
                "expiresAt", VIETNAM_DATE_TIME.format(expiresAt)
        ));
    }

    private void sendTemplate(String toEmail, String templateCode, Map<String, String> tokens) {
        EmailTemplateEntity template = emailTemplateRepository.findByCodeAndIsActiveTrue(templateCode)
                .orElseThrow(() -> new IllegalStateException("Active " + templateCode + " email template is required"));
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromHeader());
        message.setTo(toEmail);
        message.setSubject(render(template.getSubjectTemplate(), tokens));
        message.setText(render(template.getBodyTemplate(), tokens));
        mailSender.send(message);
    }

    private String fromHeader() {
        return fromName == null || fromName.isBlank()
                ? fromEmail
                : "%s <%s>".formatted(fromName.trim(), fromEmail);
    }

    private String greetingName(String fullName) {
        if (fullName == null || fullName.isBlank() || fullName.contains("@")) return "bạn";
        return fullName.trim();
    }

    private String render(String value, Map<String, String> tokens) {
        String rendered = value;
        for (Map.Entry<String, String> token : tokens.entrySet()) {
            rendered = rendered.replace("{{" + token.getKey() + "}}", token.getValue());
        }
        return rendered;
    }
}
