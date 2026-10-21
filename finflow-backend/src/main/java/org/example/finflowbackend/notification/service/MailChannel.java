package org.example.finflowbackend.notification.service;

import org.example.finflowbackend.notification.NotificationProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.env.Environment;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.stereotype.Component;

/**
 * 邮件渠道（S33）：SMTP 未配置（spring.mail.host 为空）时降级为只记日志（SKIPPED），
 * 站内信不受影响；SMTP 已配置但发送异常记 FAILED，不中断通知流程。
 */
@Component
public class MailChannel {

    /** 邮件渠道结果：状态 + 失败原因。 */
    public record MailResult(String status, String error) {
        public static MailResult sent() {
            return new MailResult("SENT", null);
        }

        public static MailResult skipped(String reason) {
            return new MailResult("SKIPPED", reason);
        }

        public static MailResult failed(String error) {
            return new MailResult("FAILED", error);
        }
    }

    private static final Logger log = LoggerFactory.getLogger(MailChannel.class);

    private final JavaMailSender mailSender;
    private final boolean configured;
    private final String from;

    public MailChannel(ObjectProvider<JavaMailSender> mailSenderProvider,
                       Environment environment,
                       NotificationProperties properties) {
        this.mailSender = mailSenderProvider.getIfAvailable();
        // MailSenderAutoConfiguration 在引入 starter 后总会创建 bean，host 为空时发送必然失败，
        // 所以以 spring.mail.host 是否配置作为降级判据
        String host = environment.getProperty("spring.mail.host");
        this.configured = mailSender != null && host != null && !host.isBlank();
        this.from = properties.mailFrom() != null
                ? properties.mailFrom()
                : environment.getProperty("spring.mail.username");
    }

    public MailResult send(String to, String subject, String text) {
        if (!configured) {
            log.info("[通知邮件] SMTP 未配置，跳过发送（收件人 {}）: {}", to, subject);
            return MailResult.skipped("SMTP 未配置");
        }
        if (to == null || to.isBlank()) {
            return MailResult.skipped("收件人无邮箱");
        }
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(from);
            message.setTo(to);
            message.setSubject(subject);
            message.setText(text);
            mailSender.send(message);
            return MailResult.sent();
        } catch (Exception e) {
            log.warn("[通知邮件] 发送失败（收件人 {}）: {}", to, e.getMessage());
            return MailResult.failed(e.getMessage());
        }
    }
}
