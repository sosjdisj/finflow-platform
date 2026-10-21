package org.example.finflowbackend.audit.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.example.finflowbackend.audit.dto.AuditLogResponse;
import org.example.finflowbackend.audit.entity.AuditLog;
import org.example.finflowbackend.audit.mapper.AuditLogMapper;
import org.example.finflowbackend.common.api.PageResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 审计日志查询（S35）：只读，管理端分页筛选与导出取数共用一套过滤条件。
 */
@Service
public class AuditQueryService {

    private final AuditLogMapper auditLogMapper;

    public AuditQueryService(AuditLogMapper auditLogMapper) {
        this.auditLogMapper = auditLogMapper;
    }

    @Transactional(readOnly = true)
    public PageResponse<AuditLogResponse> page(long page, long size, String actorUsername, String action,
                                               String resourceType, String result,
                                               LocalDateTime startTime, LocalDateTime endTime) {
        Page<AuditLog> pageResult = auditLogMapper.selectPage(new Page<>(page, size),
                wrapper(actorUsername, action, resourceType, result, startTime, endTime)
                        .orderByDesc(AuditLog::getId));
        return PageResponse.of(pageResult.convert(AuditLogResponse::from));
    }

    /** 导出取数：同一套筛选，限量（调用量由导出上限控制），按 id 升序保证可读顺序。 */
    @Transactional(readOnly = true)
    public List<AuditLog> listForExport(String actorUsername, String action, String resourceType,
                                        String result, LocalDateTime startTime, LocalDateTime endTime,
                                        int limit) {
        return auditLogMapper.selectList(wrapper(actorUsername, action, resourceType, result, startTime, endTime)
                .orderByAsc(AuditLog::getId)
                .last("LIMIT " + limit));
    }

    private LambdaQueryWrapper<AuditLog> wrapper(String actorUsername, String action, String resourceType,
                                                 String result, LocalDateTime startTime, LocalDateTime endTime) {
        return new LambdaQueryWrapper<AuditLog>()
                .eq(actorUsername != null && !actorUsername.isBlank(), AuditLog::getActorUsername, actorUsername)
                .eq(action != null && !action.isBlank(), AuditLog::getAction, action)
                .eq(resourceType != null && !resourceType.isBlank(), AuditLog::getResourceType, resourceType)
                .eq(result != null && !result.isBlank(), AuditLog::getResult, result)
                .ge(startTime != null, AuditLog::getCreatedAt, startTime)
                .le(endTime != null, AuditLog::getCreatedAt, endTime);
    }
}
