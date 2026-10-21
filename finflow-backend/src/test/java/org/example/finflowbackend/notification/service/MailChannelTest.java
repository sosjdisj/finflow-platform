package org.example.finflowbackend.notification.service;

import org.example.finflowbackend.notification.NotificationProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.env.Environment;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 邮件渠道单测（S33）：SMTP 未配置降级 SKIPPED、正常发送 SENT、发送异常 FAILED。
 */
class MailChannelTest {

    @SuppressWarnings("unchecked")
    private MailChannel channel(boolean hostConfigured, JavaMailSender mailSender) {
        ObjectProvider<JavaMailSender> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(mailSender);
        Environment environment = mock(Environment.class);
        when(environment.getProperty("spring.mail.host")).thenReturn(hostConfigured ? "smtp.test.local" : null);
        when(environment.getProperty("spring.mail.username")).thenReturn("noreply@finflow.test");
        return new MailChannel(provider, environment, new NotificationProperties(null, null));
    }

    @Test
    void skipsWhenSmtpNotConfigured() {
        MailChannel.MailResult result = channel(false, mock(JavaMailSender.class))
                .send("m@test.com", "标题", "正文");

        assertEquals("SKIPPED", result.status());
        assertEquals("SMTP 未配置", result.error());
    }

    @Test
    void skipsWhenRecipientHasNoEmail() {
        MailChannel.MailResult result = channel(true, mock(JavaMailSender.class))
                .send(null, "标题", "正文");

        assertEquals("SKIPPED", result.status());
    }

    @Test
    void sendsAndReturnsSent() {
        JavaMailSender mailSender = mock(JavaMailSender.class);

        MailChannel.MailResult result = channel(true, mailSender)
                .send("m@test.com", "标题", "正文");

        assertEquals("SENT", result.status());
        verify(mailSender).send(any(SimpleMailMessage.class));
    }

    @Test
    void smtpErrorReturnsFailedWithError() {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        doThrow(new IllegalStateException("connection refused"))
                .when(mailSender).send(any(SimpleMailMessage.class));

        MailChannel.MailResult result = channel(true, mailSender)
                .send("m@test.com", "标题", "正文");

        assertEquals("FAILED", result.status());
        assertEquals("connection refused", result.error());
    }
}
