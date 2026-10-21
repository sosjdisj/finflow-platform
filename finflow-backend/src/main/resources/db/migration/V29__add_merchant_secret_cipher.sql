-- ============================================================
-- S32 Webhook 签名需要明文 Secret（HMAC-SHA256），而 Secret 此前只存 SHA-256 哈希
-- （明文仅创建时返回一次）。增加 secret_cipher 列：AES-256-GCM 可逆加密存储，
-- 密钥走环境变量 FINFLOW_SECRET_ENCRYPT_KEY（application.yaml finflow.security）。
-- secret_hash 保留（语义不变）；S32 之前创建的历史凭证无密文，商户重新生成凭证即可。
-- ============================================================
ALTER TABLE merchant_api_key
    ADD COLUMN secret_cipher VARCHAR(255) DEFAULT NULL COMMENT 'Secret 密文（AES-256-GCM，v1:iv:cipher 格式；Webhook 签名解密用）' AFTER secret_hash;
