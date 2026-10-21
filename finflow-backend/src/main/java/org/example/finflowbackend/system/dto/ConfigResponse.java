package org.example.finflowbackend.system.dto;

import org.example.finflowbackend.system.entity.SystemConfig;

import java.time.LocalDateTime;

/** 系统配置项响应（S37）。 */
public record ConfigResponse(String configKey, String configValue, String valueType,
                             String description, Long updatedBy, LocalDateTime updatedAt) {

    public static ConfigResponse from(SystemConfig config) {
        return new ConfigResponse(config.getConfigKey(), config.getConfigValue(), config.getValueType(),
                config.getDescription(), config.getUpdatedBy(), config.getUpdatedAt());
    }
}
