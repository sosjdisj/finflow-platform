package org.example.finflowrisk;

import org.example.finflowbackend.risk.service.RiskDecisionService;
import org.example.finflowbackend.risk.service.RiskEngine;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Import;

/**
 * 风控服务启动类（M11）：与 backend 同源（risk 模块代码零复制），打包成独立可执行 jar
 * （{@code -risk.jar}）作为独立进程运行（端口 8093，Nacos 注册名 finflow-risk）。
 *
 * <p>只装配「决策」链路：规则求值器、规则 / 事件 Mapper、限时决策入口与内部决策端点；
 * 规则管理、人工审核、事件列表仍留在 backend（本服务不扫 risk.controller / risk.service 其余 Bean），
 * 因此不依赖 AuditApi、payment api。M14 起风控阈值与流控阈值直接从 Nacos Config 读取
 * （与 backend 共用 finflow-common.yaml），不再回调 backend 的配置端点。
 * Flyway 迁移仍由 backend 执行（{@code spring.flyway.enabled=false}，见 application-risk.yaml）。</p>
 *
 * <p>与 M08 渠道服务不同，风控求值依赖 Redis（频次 / 同 IP 计数）与 Kafka（拦截事件），
 * 因此不排除对应自动配置。</p>
 */
@SpringBootApplication(scanBasePackages = {
        "org.example.finflowbackend.risk.evaluate",
        "org.example.finflowbackend.risk.mapper",
        "org.example.finflowbackend.risk.config",
        "org.example.finflowbackend.risk.web",
        "org.example.finflowbackend.common.tunable",
        "org.example.finflowbackend.common.event",
        // M15：RiskEngine 经 DomainEventPublisher 发事件，需要 TracePropagation 注入 traceparent
        "org.example.finflowbackend.common.trace",
        "org.example.finflowrisk"
})
@Import({RiskEngine.class, RiskDecisionService.class})
@ConfigurationPropertiesScan("org.example.finflowbackend.risk.config")
@MapperScan("org.example.finflowbackend.risk.mapper")
public class RiskServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(RiskServiceApplication.class, args);
    }
}
