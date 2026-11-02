package org.example.finflowbackend.notification.remote;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Configuration;

/**
 * 远程通知管理模式装配（M16）：只有 {@code finflow.notify.mode=remote} 时才注册 Feign 客户端。
 *
 * <p>开关放在本配置类而不是 {@link NotificationAdminClient} 接口上：Feign 客户端由
 * {@code @EnableFeignClients} 的注册器按接口注解直接注册 BeanDefinition，接口上的
 * {@code @Conditional} 不生效。放进条件配置类后，local 模式下客户端不会被注册，
 * 与 {@link org.example.finflowbackend.notification.service.LocalNotificationAdmin} 互斥，
 * 容器里始终只有一个 {@code NotificationAdminApi}。</p>
 */
@Configuration
@ConditionalOnProperty(name = "finflow.notify.mode", havingValue = "remote")
@EnableFeignClients(clients = NotificationAdminClient.class)
public class NotificationAdminRemoteConfig {
}
