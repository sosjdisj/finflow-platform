package org.example.finflowbackend.audit.api;

/**
 * 审计快照提交的对外能力（M03 facade）：业务模块在变更处提交改前/改后快照，
 * 由 audit 模块的切面统一落库，其他模块不感知存储细节。
 */
public interface AuditApi {

    /** 提交改前/改后快照（传对象/Map/记录均可，null 表示无快照）。 */
    void submit(Object before, Object after);
}
