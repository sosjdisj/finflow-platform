package org.example.finflowbackend.system.controller;

import java.time.LocalDateTime;

/**
 * 健康检查返回体。
 */
public record HealthStatusVO(String status, LocalDateTime timestamp) {
}
