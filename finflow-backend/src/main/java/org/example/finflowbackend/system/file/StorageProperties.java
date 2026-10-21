package org.example.finflowbackend.system.file;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 对象存储配置（S38）。经启动类 @ConfigurationPropertiesScan 注册。
 * endpoint 指向 docker-compose 里的 MinIO（9000 S3 API 端口）。
 */
@ConfigurationProperties(prefix = "finflow.storage")
public record StorageProperties(String endpoint, String accessKey, String secretKey, String bucket) {

    public StorageProperties {
        if (endpoint == null || endpoint.isBlank()) {
            endpoint = "http://localhost:9000";
        }
        if (accessKey == null || accessKey.isBlank()) {
            accessKey = "finflow";
        }
        if (secretKey == null || secretKey.isBlank()) {
            secretKey = "finflow123";
        }
        if (bucket == null || bucket.isBlank()) {
            bucket = "finflow";
        }
    }
}
