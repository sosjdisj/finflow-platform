package org.example.finflowbackend.audit.dto;

import org.example.finflowbackend.audit.entity.AuditLog;

import java.time.LocalDateTime;

/**
 * 审计日志响应（只读，不含任何改写入口）。
 */
public record AuditLogResponse(Long id, String actorUsername, String action, String resourceType,
                               String resourceId, String beforeSnapshot, String afterSnapshot,
                               String result, String errorMessage, String clientIp,
                               LocalDateTime createdAt) {

    public static AuditLogResponse from(AuditLog log) {
        return new AuditLogResponse(log.getId(), log.getActorUsername(), log.getAction(),
                log.getResourceType(), log.getResourceId(), log.getBeforeSnapshot(),
                log.getAfterSnapshot(), log.getResult(), log.getErrorMessage(),
                log.getClientIp(), log.getCreatedAt());
    }
}
