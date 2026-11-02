package org.example.finflowbackend.notify.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 商户通知配置投影（表 finflow_notify.notify_merchant_config，M16）。
 * 由 backend 发布的 merchant.notify-config.changed 事件写入，通知服务只读本表——
 * 避免 webhook 投递 / 通知邮件同步回查 backend 的商户与凭证（跨库零耦合）。
 */
@Getter
@Setter
@TableName(value = "notify_merchant_config", schema = "finflow_notify")
public class NotifyMerchantConfig {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long merchantId;

    /** Webhook 回调地址；为空表示商户未订阅。 */
    private String webhookUrl;

    /** 联系邮箱（商户通知邮件收件人）。 */
    private String contactEmail;

    /** API 签名密钥的 AES-GCM 密文（读取时用共享密钥解密）。 */
    private String secretCipher;

    /** 配置变更时间（事件携带）。 */
    private LocalDateTime changedAt;

    private LocalDateTime updatedAt;
}
