package org.example.finflowbackend.auth.token;

import io.jsonwebtoken.JwtException;
import org.example.finflowbackend.auth.entity.SysUser;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * JWT 组件单测：签发/解析回路、过期拒绝、篡改拒绝、刷新令牌哈希。
 */
class JwtTokenProviderTest {

    private static final String SECRET = "unit-test-secret-0123456789abcdefghijklmn";

    @Test
    void accessTokenRoundTripCarriesUserIdAndUsername() {
        JwtTokenProvider provider = providerWith(Duration.ofMinutes(30));

        String token = provider.issueAccessToken(user(7L, "alice"));
        CurrentUser current = provider.parseAccessToken(token);

        assertEquals(7L, current.userId());
        assertEquals("alice", current.username());
    }

    @Test
    void expiredAccessTokenIsRejected() {
        JwtTokenProvider provider = providerWith(Duration.ofSeconds(-1));

        String token = provider.issueAccessToken(user(7L, "alice"));

        assertThrows(JwtException.class, () -> provider.parseAccessToken(token));
    }

    @Test
    void tamperedAccessTokenIsRejected() {
        JwtTokenProvider provider = providerWith(Duration.ofMinutes(30));
        String token = provider.issueAccessToken(user(7L, "alice"));

        String[] parts = token.split("\\.");
        char flipped = parts[1].charAt(0) == 'A' ? 'B' : 'A';
        String tampered = parts[0] + "." + flipped + parts[1].substring(1) + "." + parts[2];

        assertThrows(JwtException.class, () -> provider.parseAccessToken(tampered));
    }

    @Test
    void refreshTokenIsRandomAndHashedDeterministically() {
        JwtTokenProvider provider = providerWith(Duration.ofMinutes(30));

        String first = provider.newRefreshToken();
        String second = provider.newRefreshToken();

        assertNotEquals(first, second);
        assertEquals(JwtTokenProvider.hash(first), JwtTokenProvider.hash(first));
        assertEquals(64, JwtTokenProvider.hash(first).length());
    }

    private static JwtTokenProvider providerWith(Duration accessTtl) {
        JwtProperties properties = new JwtProperties();
        properties.setSecret(SECRET);
        properties.setAccessTokenTtl(accessTtl);
        properties.setRefreshTokenTtl(Duration.ofDays(7));
        return new JwtTokenProvider(properties);
    }

    private static SysUser user(Long id, String username) {
        SysUser user = new SysUser();
        user.setId(id);
        user.setUsername(username);
        return user;
    }
}
