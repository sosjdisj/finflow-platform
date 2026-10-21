package org.example.finflowbackend.payment.channel;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 真实渠道对接配置（finflow.remote-channel.*）：
 * baseUrl 为渠道服务基址（本地假渠道模拟器，默认 8090）；secret 为签名共享密钥，两端同值；
 * 超时控制 HTTP 客户端连接与读取。经启动类 @ConfigurationPropertiesScan 注册。
 */
@ConfigurationProperties(prefix = "finflow.remote-channel")
public record RemoteChannelProperties(String baseUrl, String secret, int connectTimeoutMs, int readTimeoutMs) {

    public RemoteChannelProperties {
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = "http://localhost:8090";
        }
        if (secret == null || secret.isBlank()) {
            secret = "finflow-fake-channel-secret";
        }
        if (connectTimeoutMs <= 0) {
            connectTimeoutMs = 3000;
        }
        if (readTimeoutMs <= 0) {
            readTimeoutMs = 5000;
        }
    }
}
