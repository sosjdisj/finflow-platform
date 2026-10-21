package org.example.finflowbackend.auth.dto;

/**
 * 令牌响应。
 *
 * @param accessToken  短期 JWT，放入 Authorization: Bearer {token}
 * @param refreshToken 长期刷新令牌明文，仅此一次返回给客户端
 * @param tokenType    固定 Bearer
 * @param expiresIn    accessToken 有效期（秒）
 */
public record TokenResponse(String accessToken, String refreshToken, String tokenType, long expiresIn) {
}
