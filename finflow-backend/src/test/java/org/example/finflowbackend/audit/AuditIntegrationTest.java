package org.example.finflowbackend.audit;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.example.finflowbackend.audit.annotation.Audited;
import org.example.finflowbackend.audit.entity.AuditLog;
import org.example.finflowbackend.audit.mapper.AuditLogMapper;
import org.example.finflowbackend.auth.token.CurrentUser;
import org.example.finflowbackend.support.IntegrationTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 审计链路集成验收（依赖本地 MySQL，S39 换 Testcontainers）：
 * 带快照的成功操作、抛异常的失败操作都落 audit_log，ThreadLocal 快照随切面清理。
 */
class AuditIntegrationTest extends IntegrationTestBase {

    private static final String ACTION_OK = "IT_AUDIT_SAMPLE";
    private static final String ACTION_FAIL = "IT_AUDIT_FAIL";

    @Autowired
    private AuditedTestBean auditedTestBean;

    @Autowired
    private AuditLogMapper auditLogMapper;

    @AfterEach
    void cleanUp() {
        SecurityContextHolder.clearContext();
        auditLogMapper.delete(new LambdaQueryWrapper<AuditLog>()
                .in(AuditLog::getAction, List.of(ACTION_OK, ACTION_FAIL)));
    }

    @Test
    void auditedMethodWritesSnapshotLog() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(new CurrentUser(2L, "admin"), null));

        auditedTestBean.touch("res-1", Map.of("rateBps", 150), Map.of("rateBps", 120));

        AuditLog entry = auditLogMapper.selectOne(new LambdaQueryWrapper<AuditLog>()
                .eq(AuditLog::getAction, ACTION_OK));
        assertEquals("admin", entry.getActorUsername());
        assertEquals("ROLE", entry.getResourceType());
        assertEquals("res-1", entry.getResourceId());
        assertEquals("SUCCESS", entry.getResult());
        assertEquals("{\"rateBps\":150}", entry.getBeforeSnapshot());
        assertEquals("{\"rateBps\":120}", entry.getAfterSnapshot());
    }

    @Test
    void failedMethodWritesFailedLog() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(new CurrentUser(2L, "admin"), null));

        assertThrows(IllegalStateException.class, () -> auditedTestBean.boom("res-2"));

        AuditLog entry = auditLogMapper.selectOne(new LambdaQueryWrapper<AuditLog>()
                .eq(AuditLog::getAction, ACTION_FAIL));
        assertEquals("FAILED", entry.getResult());
        assertEquals("boom", entry.getErrorMessage());
    }

    @TestConfiguration
    static class AuditedTestBeanConfig {

        @Bean
        AuditedTestBean auditedTestBean(AuditRecorder auditRecorder) {
            return new AuditedTestBean(auditRecorder);
        }
    }

    /** 切面端到端验证用最小打点目标（无业务含义）。 */
    static class AuditedTestBean {

        private final AuditRecorder auditRecorder;

        AuditedTestBean(AuditRecorder auditRecorder) {
            this.auditRecorder = auditRecorder;
        }

        @Audited(action = ACTION_OK, resourceType = "ROLE", resourceId = "#resourceId")
        public void touch(String resourceId, Object before, Object after) {
            auditRecorder.submit(before, after);
        }

        @Audited(action = ACTION_FAIL, resourceType = "ROLE", resourceId = "#resourceId")
        public void boom(String resourceId) {
            throw new IllegalStateException("boom");
        }
    }
}
