package org.example.finflowbackend.notify.api;

/**
 * 商户通知配置（M16 契约）：webhook 投递与通知邮件需要的商户侧配置快照。
 *
 * @param webhookUrl    Webhook 回调地址（空 = 未订阅）
 * @param contactEmail  联系邮箱（空 = 不发送邮件）
 * @param signingSecret 签名密钥明文（空 = 历史凭证无密文，无法签名投递）
 */
public record NotifyConfig(String webhookUrl, String contactEmail, String signingSecret) {
}
