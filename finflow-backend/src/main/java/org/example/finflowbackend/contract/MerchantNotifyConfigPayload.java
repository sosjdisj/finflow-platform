package org.example.finflowbackend.contract;

import java.time.LocalDateTime;

/**
 * 商户通知配置事件（主题 {@link EventTopics#MERCHANT_NOTIFY_CONFIG}，M16）。
 *
 * <p>商户资料（回调地址 / 联系邮箱）或 API 凭证变更后由 merchant 模块发布，
 * 通知服务消费后落自己的 notify_merchant_config 投影。这样 webhook 投递与通知邮件
 * 都只读本服务数据，不再同步回查 backend。</p>
 *
 * @param merchantId    商户 ID
 * @param webhookUrl    Webhook 回调地址（可为空，表示未订阅）
 * @param contactEmail  联系邮箱（商户通知邮件收件人，可为空）
 * @param secretCipher  API 签名密钥的 AES-GCM 密文（与 merchant_api_key.secret_cipher 同值，
 *                      消费端用共享的 finflow.security.secret-encrypt-key 解密；可为空表示历史凭证无密文）
 * @param changedAt     配置变更时间
 */
public record MerchantNotifyConfigPayload(Long merchantId, String webhookUrl, String contactEmail,
                                         String secretCipher, LocalDateTime changedAt) {
}
