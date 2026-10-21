package org.example.finflowbackend.audit.aspect;

import jakarta.servlet.http.HttpServletRequest;
import org.example.finflowbackend.audit.AuditRecorder;
import org.example.finflowbackend.audit.annotation.Audited;
import org.example.finflowbackend.audit.entity.AuditLog;
import org.example.finflowbackend.audit.service.AuditWriter;
import org.example.finflowbackend.auth.token.CurrentUser;
import org.example.finflowbackend.auth.web.CurrentUserHolder;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 审计切面（S35）：环绕 {@code @Audited} 接口，成功记 SUCCESS、抛异常记 FAILED 并原样抛出。
 * 操作人取当前登录用户，IP 取 X-Forwarded-For 首值（反代场景）否则 remoteAddr，
 * 快照取业务 Service 经 {@link AuditRecorder#submit} 提交的内容。
 * 审计写失败只告警不影响业务响应；finally 清理 ThreadLocal 防线程复用串数据。
 */
@Aspect
@Component
public class AuditAspect {

    private static final Logger log = LoggerFactory.getLogger(AuditAspect.class);
    private static final int ERROR_MESSAGE_MAX = 255;
    private static final int RESOURCE_ID_MAX = 64;
    private static final String RESULT_SUCCESS = "SUCCESS";
    private static final String RESULT_FAILED = "FAILED";

    private final AuditRecorder auditRecorder;
    private final AuditWriter auditWriter;
    private final SpelExpressionParser spelParser = new SpelExpressionParser();

    public AuditAspect(AuditRecorder auditRecorder, AuditWriter auditWriter) {
        this.auditRecorder = auditRecorder;
        this.auditWriter = auditWriter;
    }

    @Around("@annotation(audited)")
    public Object audit(ProceedingJoinPoint joinPoint, Audited audited) throws Throwable {
        CurrentUser actor = currentActorOrNull();
        try {
            Object result = joinPoint.proceed();
            write(actor, audited, joinPoint, RESULT_SUCCESS, null);
            return result;
        } catch (Throwable e) {
            write(actor, audited, joinPoint, RESULT_FAILED, truncate(e.getMessage(), ERROR_MESSAGE_MAX));
            throw e;
        } finally {
            auditRecorder.clear();
        }
    }

    private void write(CurrentUser actor, Audited audited, ProceedingJoinPoint joinPoint,
                       String result, String errorMessage) {
        try {
            AuditRecorder.Snapshots snapshots = auditRecorder.drain();
            AuditLog entry = new AuditLog();
            entry.setActorId(actor == null ? 0L : actor.userId());
            entry.setActorUsername(actor == null ? "unknown" : actor.username());
            entry.setAction(audited.action());
            entry.setResourceType(audited.resourceType());
            entry.setResourceId(resolveResourceId(audited, joinPoint));
            entry.setBeforeSnapshot(snapshots == null ? null : snapshots.before());
            entry.setAfterSnapshot(snapshots == null ? null : snapshots.after());
            entry.setResult(result);
            entry.setErrorMessage(errorMessage);
            entry.setClientIp(resolveClientIp());
            auditWriter.write(entry);
        } catch (Exception e) {
            log.warn("[审计] 写入失败（不影响业务）: action={}, resource={}/{}",
                    audited.action(), audited.resourceType(), audited.resourceId(), e);
        }
    }

    private CurrentUser currentActorOrNull() {
        try {
            return CurrentUserHolder.current();
        } catch (Exception e) {
            // @Audited 接口均在鉴权之后，理论不会走到；防御性兜底
            return null;
        }
    }

    /** 资源标识按注解里的 SpEL 从方法参数解析；解析失败返回空串，不影响业务。 */
    private String resolveResourceId(Audited audited, ProceedingJoinPoint joinPoint) {
        String expression = audited.resourceId();
        if (expression.isBlank()) {
            return "";
        }
        try {
            MethodSignature signature = (MethodSignature) joinPoint.getSignature();
            StandardEvaluationContext context = new StandardEvaluationContext();
            String[] paramNames = signature.getParameterNames();
            Object[] args = joinPoint.getArgs();
            for (int i = 0; i < paramNames.length; i++) {
                context.setVariable(paramNames[i], args[i]);
            }
            String value = spelParser.parseExpression(expression).getValue(context, String.class);
            return truncate(value, RESOURCE_ID_MAX);
        } catch (Exception e) {
            log.warn("[审计] resourceId SpEL 解析失败: {}", expression, e);
            return "";
        }
    }

    private String resolveClientIp() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            HttpServletRequest request = attributes.getRequest();
            String forwarded = request.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                return forwarded.split(",")[0].trim();
            }
            return request.getRemoteAddr();
        }
        return null;
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
