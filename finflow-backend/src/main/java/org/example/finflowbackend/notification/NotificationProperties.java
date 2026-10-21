package org.example.finflowbackend.notification;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 通知中心配置（S33）。经启动类 @ConfigurationPropertiesScan 注册。
 * 邮件降级：spring.mail.host 未配置时邮件渠道自动跳过（记 SKIPPED），站内信不受影响。
 *
 * @param mailFrom      邮件发件人地址；缺省用 spring.mail.username
 * @param platformEmail 平台事件（对账异常等）收件邮箱；未配置时平台通知邮件跳过（SKIPPED），站内信不受影响
 */
@ConfigurationProperties(prefix = "finflow.notification")
public record NotificationProperties(String mailFrom, String platformEmail) {

    public NotificationProperties {
        if (mailFrom == null || mailFrom.isBlank()) {
            mailFrom = null;
        }
        if (platformEmail == null || platformEmail.isBlank()) {
            platformEmail = null;
        }
    }
}
