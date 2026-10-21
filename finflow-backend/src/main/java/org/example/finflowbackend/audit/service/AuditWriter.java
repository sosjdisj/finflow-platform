package org.example.finflowbackend.audit.service;

import org.example.finflowbackend.audit.entity.AuditLog;
import org.example.finflowbackend.audit.mapper.AuditLogMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 审计落库（S35）：统一从这里写 audit_log，独立新事务（REQUIRES_NEW）——
 * 业务事务已提交/回滚后才写审计，审计写失败只告警不回滚业务（见切面）。
 */
@Service
public class AuditWriter {

    private final AuditLogMapper auditLogMapper;

    public AuditWriter(AuditLogMapper auditLogMapper) {
        this.auditLogMapper = auditLogMapper;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void write(AuditLog entry) {
        auditLogMapper.insert(entry);
    }
}
