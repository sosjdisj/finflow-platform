package org.example.finflowbackend.auth.token;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * JWT 配置：finflow.auth.jwt.*。
 * secret 生产环境必须用环境变量覆盖（见 application-prod.yaml）。
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "finflow.auth.jwt")
public class JwtProperties {

    /** HMAC 签名密钥，长度至少 32 字节（HS256 要求）。 */
    private String secret;

    private Duration accessTokenTtl = Duration.ofMinutes(30);

    private Duration refreshTokenTtl = Duration.ofDays(7);
}
