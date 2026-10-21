package org.example.finflowbackend.auth.dto;

import org.example.finflowbackend.auth.entity.SysLoginLog;

import java.time.LocalDateTime;

/** 登录日志条目。 */
public record LoginLogResponse(Long id, String username, Long userId, boolean success,
                               String failReason, String ip, String userAgent, LocalDateTime createdAt) {

    public static LoginLogResponse from(SysLoginLog log) {
        return new LoginLogResponse(log.getId(), log.getUsername(), log.getUserId(),
                log.getSuccess() != null && log.getSuccess() == 1,
                log.getFailReason(), log.getIp(), log.getUserAgent(), log.getCreatedAt());
    }
}
