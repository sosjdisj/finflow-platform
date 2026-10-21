package org.example.finflowbackend.merchant.dto;

import java.time.LocalDateTime;

/**
 * 凭证创建响应：secret 明文仅此一次返回，之后无法再取。
 */
public record ApiKeyCreateResponse(Long id, Long merchantId, String apiKey,
                                   String secret, LocalDateTime createdAt) {
}
