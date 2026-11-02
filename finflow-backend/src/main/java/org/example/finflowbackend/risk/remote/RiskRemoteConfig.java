package org.example.finflowbackend.risk.remote;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Configuration;

/**
 * 远程风控模式装配（M11）：只有 {@code finflow.risk.mode=remote} 时才注册 Feign 客户端。
 *
 * <p>开关放在本配置类而不是 {@link RiskDecisionClient} 接口上：Feign 客户端由
 * {@code @EnableFeignClients} 的注册器按接口注解直接注册 BeanDefinition，接口上的
 * {@code @Conditional} 不生效；把 {@code @EnableFeignClients} 放进条件配置类，
 * local 模式下客户端根本不会被注册，与 {@link org.example.finflowbackend.risk.service.RiskDecisionService}
 * 互斥，保证容器里始终只有一个 {@code RiskDecisionApi}。</p>
 */
@Configuration
@ConditionalOnProperty(name = "finflow.risk.mode", havingValue = "remote")
@EnableFeignClients(clients = RiskDecisionClient.class)
public class RiskRemoteConfig {
}
