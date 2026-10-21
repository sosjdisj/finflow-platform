package org.example.finflowbackend.audit.aspect;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.example.finflowbackend.audit.AuditRecorder;
import org.example.finflowbackend.audit.annotation.Audited;
import org.example.finflowbackend.audit.entity.AuditLog;
import org.example.finflowbackend.audit.service.AuditWriter;
import org.example.finflowbackend.auth.token.CurrentUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 审计切面单测：成功/失败双路径、快照与 SpEL 资源标识落库、ThreadLocal 清理、
 * 审计写失败不影响业务返回。
 */
class AuditAspectTest {

    private final AuditRecorder auditRecorder = new AuditRecorder(JsonMapper.builder().build());
    private final AuditWriter auditWriter = mock(AuditWriter.class);
    private final AuditAspect aspect = new AuditAspect(auditRecorder, auditWriter);

    private final ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
    private final MethodSignature signature = mock(MethodSignature.class);
    private final Audited audited = mock(Audited.class);

    @AfterEach
    void cleanUp() {
        auditRecorder.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void successWritesSnapshotLogWithSpelResourceId() throws Throwable {
        loginAsAdmin();
        stubJoinPoint(new Object[]{42L});
        when(audited.action()).thenReturn("MERCHANT_RATE_SET");
        when(audited.resourceType()).thenReturn("MERCHANT");
        when(audited.resourceId()).thenReturn("#merchantId");
        when(joinPoint.proceed()).thenReturn("ok");
        auditRecorder.submit(Map.of("rateBps", 150), Map.of("rateBps", 120));

        Object result = aspect.audit(joinPoint, audited);

        assertEquals("ok", result);
        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditWriter).write(captor.capture());
        AuditLog entry = captor.getValue();
        assertEquals(2L, entry.getActorId());
        assertEquals("admin", entry.getActorUsername());
        assertEquals("MERCHANT_RATE_SET", entry.getAction());
        assertEquals("MERCHANT", entry.getResourceType());
        assertEquals("42", entry.getResourceId());
        assertEquals("SUCCESS", entry.getResult());
        assertEquals("{\"rateBps\":150}", entry.getBeforeSnapshot());
        assertEquals("{\"rateBps\":120}", entry.getAfterSnapshot());
        assertNull(entry.getErrorMessage());
        assertNull(entry.getClientIp());
        // 切面取走并清理快照，线程复用不串数据
        assertNull(auditRecorder.drain());
    }

    @Test
    void failureWritesFailedLogAndRethrows() throws Throwable {
        loginAsAdmin();
        stubJoinPoint(new Object[]{7L});
        when(audited.action()).thenReturn("LEDGER_ADJUST");
        when(audited.resourceType()).thenReturn("ACCOUNT");
        when(audited.resourceId()).thenReturn("#merchantId");
        RuntimeException boom = new RuntimeException("扣减失败");
        when(joinPoint.proceed()).thenThrow(boom);

        assertThrows(RuntimeException.class, () -> aspect.audit(joinPoint, audited));

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditWriter).write(captor.capture());
        AuditLog entry = captor.getValue();
        assertEquals("FAILED", entry.getResult());
        assertEquals("扣减失败", entry.getErrorMessage());
        assertNull(entry.getBeforeSnapshot());
        assertNull(auditRecorder.drain());
    }

    @Test
    void noSnapshotStillWrites() throws Throwable {
        loginAsAdmin();
        stubJoinPoint(new Object[]{1L});
        when(audited.action()).thenReturn("SETTLEMENT_APPROVE");
        when(audited.resourceType()).thenReturn("SETTLEMENT");
        when(audited.resourceId()).thenReturn("#merchantId");
        when(joinPoint.proceed()).thenReturn("ok");

        aspect.audit(joinPoint, audited);

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditWriter).write(captor.capture());
        assertEquals("SUCCESS", captor.getValue().getResult());
        assertNull(captor.getValue().getBeforeSnapshot());
        assertNull(captor.getValue().getAfterSnapshot());
    }

    @Test
    void auditWriteFailureDoesNotBreakBusiness() throws Throwable {
        loginAsAdmin();
        stubJoinPoint(new Object[]{1L});
        when(audited.action()).thenReturn("MERCHANT_RATE_SET");
        when(audited.resourceType()).thenReturn("MERCHANT");
        when(audited.resourceId()).thenReturn("");
        when(joinPoint.proceed()).thenReturn("ok");
        doThrow(new IllegalStateException("库连不上")).when(auditWriter).write(any(AuditLog.class));

        assertEquals("ok", aspect.audit(joinPoint, audited));
    }

    private void stubJoinPoint(Object[] args) {
        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.getParameterNames()).thenReturn(new String[]{"merchantId"});
        when(joinPoint.getArgs()).thenReturn(args);
    }

    private void loginAsAdmin() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(new CurrentUser(2L, "admin"), null));
    }
}
