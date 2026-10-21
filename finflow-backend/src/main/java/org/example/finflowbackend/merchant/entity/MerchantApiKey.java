package org.example.finflowbackend.merchant.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 商户 API 凭证（表 merchant_api_key）。对应迁移脚本 V8__create_merchant_api_key.sql。
 * api_key 明文存储；secret 存 SHA-256 哈希 + AES-256-GCM 密文（S32 起，Webhook 签名解密用），
 * 明文仅创建时返回一次。
 */
@Getter
@Setter
@TableName("merchant_api_key")
public class MerchantApiKey {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long merchantId;

    private String apiKey;

    private String secretHash;

    /** Secret 密文（AES-256-GCM，v1:iv:cipher 格式），历史凭证可能为空。 */
    private String secretCipher;

    /** IP/CIDR 白名单，逗号分隔。 */
    private String ipWhitelist;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
