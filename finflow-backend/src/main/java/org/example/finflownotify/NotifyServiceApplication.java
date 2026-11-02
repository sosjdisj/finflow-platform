package org.example.finflownotify;

import org.example.finflowbackend.common.config.MybatisPlusConfig;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 通知服务启动类（M16）：与 backend 同源（webhook / notification / notify 模块代码零复制），
 * 打包成独立可执行 jar（{@code -notify.jar}）作为独立进程运行（端口 8094，Nacos 注册名 finflow-notify）。
 *
 * <p>只装配「事件消费 + 投递 + 通知落库 + 管理查询」：webhook / notification 的消费者与 Service、
 * notify 配置投影，以及本服务的内部管理端点。不扫描 payment / merchant / openapi 等资金与主数据链路，
 * 因此通知进程不持有任何同步回查能力——商户通知配置只读自己的 notify_merchant_config 投影。</p>
 *
 * <p>管理端内部端点由 backend 经 Feign 调用（{@code finflow.notify.provider=true}）；
 * 库结构迁移仍由 backend 的 Flyway 统一执行（{@code spring.flyway.enabled=false}，
 * 见 application-notify.yaml）。</p>
 */
@SpringBootApplication(scanBasePackages = {
        "org.example.finflowbackend.webhook.event",
        "org.example.finflowbackend.webhook.service",
        "org.example.finflowbackend.webhook.task",
        "org.example.finflowbackend.webhook.mapper",
        "org.example.finflowbackend.webhook.web",
        "org.example.finflowbackend.notification.event",
        "org.example.finflowbackend.notification.service",
        "org.example.finflowbackend.notification.mapper",
        "org.example.finflowbackend.notification.web",
        "org.example.finflowbackend.notify",
        "org.example.finflowbackend.common.tunable",
        "org.example.finflowbackend.common.event",
        // 消费端链路恢复（M15）与事件发布器依赖 TracePropagation / KafkaTraceRecordInterceptor
        "org.example.finflowbackend.common.trace",
        "org.example.finflowbackend.common.lock",
        // 内部端点复用统一异常响应（404 / 409 由 backend 的 Feign ErrorDecoder 还原）
        "org.example.finflowbackend.common.exception",
        "org.example.finflownotify"
})
@Import(MybatisPlusConfig.class)
@ConfigurationPropertiesScan("org.example.finflowbackend.notification")
@MapperScan({"org.example.finflowbackend.webhook.mapper",
        "org.example.finflowbackend.notification.mapper",
        "org.example.finflowbackend.notify.mapper"})
@EnableKafka
@EnableScheduling
public class NotifyServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(NotifyServiceApplication.class, args);
    }
}
