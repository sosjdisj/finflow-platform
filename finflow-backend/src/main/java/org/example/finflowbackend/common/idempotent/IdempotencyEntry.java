package org.example.finflowbackend.common.idempotent;

/**
 * 幂等键在 Redis 中的存储条目：请求指纹 + 首次响应。
 * responseBody 为 null 表示首次请求仍在处理中。
 */
record IdempotencyEntry(String fingerprint, String responseBody) {
}
