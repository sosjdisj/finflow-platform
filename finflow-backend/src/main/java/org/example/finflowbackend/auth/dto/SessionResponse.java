package org.example.finflowbackend.auth.dto;

import java.time.LocalDateTime;

/** 登录设备：一条活跃的 refresh token，id 即可踢下线的会话 ID。 */
public record SessionResponse(Long id, String deviceName, String ip, LocalDateTime createdAt) {
}
