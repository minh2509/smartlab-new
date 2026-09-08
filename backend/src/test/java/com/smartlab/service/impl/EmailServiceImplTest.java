package com.smartlab.service.impl;

import com.smartlab.entity.EmailTemplateEntity;
import com.smartlab.repo.EmailTemplateRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmailServiceImplTest {
    @Mock private JavaMailSender mailSender;
    @Mock private EmailTemplateRepository templates;

    @Test
    void sendsResetEmailFromDatabaseTemplate() {
        EmailServiceImpl service = service();
        when(templates.findByCodeAndIsActiveTrue("PASSWORD_RESET_OTP"))
                .thenReturn(Optional.of(template("Mã SmartLab", "Mã của bạn: {{otp}}")));

        service.sendResetOtpEmail("member@example.test", "482193");

        SimpleMailMessage message = capturedMessage();
        assertThat(message.getFrom()).isEqualTo("SmartLab <no-reply@smartlab.test>");
        assertThat(message.getTo()).containsExactly("member@example.test");
        assertThat(message.getSubject()).isEqualTo("Mã SmartLab");
        assertThat(message.getText()).isEqualTo("Mã của bạn: 482193");
    }

    @Test
    void rendersInvitationTemplateTokens() {
        EmailServiceImpl service = service();
        when(templates.findByCodeAndIsActiveTrue("ACCOUNT_INVITATION"))
                .thenReturn(Optional.of(template("Mời {{fullName}}", "{{inviteLink}} hết hạn {{expiresAt}}")));
        Instant expiry = Instant.parse("2026-09-10T00:00:00Z");

        service.sendInvitationEmail("member@example.test", "Nguyen Van A", "https://smartlab.test/invite", expiry);

        SimpleMailMessage message = capturedMessage();
        assertThat(message.getSubject()).isEqualTo("Mời Nguyen Van A");
        assertThat(message.getText()).isEqualTo("https://smartlab.test/invite hết hạn lúc 07:00 ngày 10/09/2026 (GMT+7)");
    }

    private EmailServiceImpl service() {
        EmailServiceImpl service = new EmailServiceImpl(mailSender, templates);
        ReflectionTestUtils.setField(service, "fromEmail", "no-reply@smartlab.test");
        ReflectionTestUtils.setField(service, "fromName", "SmartLab");
        return service;
    }

    private SimpleMailMessage capturedMessage() {
        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        return captor.getValue();
    }

    private static EmailTemplateEntity template(String subject, String body) {
        return EmailTemplateEntity.builder().code("test").subjectTemplate(subject).bodyTemplate(body).isActive(true).build();
    }
}
