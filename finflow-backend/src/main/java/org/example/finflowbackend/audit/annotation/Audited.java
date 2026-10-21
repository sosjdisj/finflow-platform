package org.example.finflowbackend.audit.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 审计打点注解（S35）：标在敏感操作接口上，由 {@code AuditAspect} 环绕记录
 * 谁/何时/IP/做了什么/结果，快照由业务 Service 内 {@code AuditRecorder.submit(before, after)} 提交。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Audited {

    /** 操作动作（如 MERCHANT_RATE_SET）。 */
    String action();

    /** 资源类型（MERCHANT/SETTLEMENT/ACCOUNT/RISK_RULE/ROLE）。 */
    String resourceType();

    /** 资源标识，支持 SpEL 引用方法参数（如 "#merchantId"、"#request.accountId"），缺省为空。 */
    String resourceId() default "";
}
