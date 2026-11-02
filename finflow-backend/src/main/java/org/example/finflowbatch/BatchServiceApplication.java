package org.example.finflowbatch;

import org.example.finflowbackend.channel.service.PaymentTransactionService;
import org.example.finflowbackend.common.config.MybatisPlusConfig;
import org.example.finflowbackend.reconciliation.service.ReconciliationMatchService;
import org.example.finflowbackend.system.service.impl.ConfigServiceImpl;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 批处理服务启动类（M17）：与 backend 同源，只装配夜间对账所需的「匹配 + 配置 + 渠道留痕查询」，
 * 打包成独立可执行 jar（{@code -batch.jar}）以独立进程运行（端口 8095，Nacos 注册名 finflow-batch）。
 *
 * <p>不扫描 payment / merchant / settlement / notification 等模块，批处理进程只持有对账匹配链路，
 * 夜间大查询与在线支付不共享线程池与堆；手工上传、查询、单任务重跑仍在 backend，
 * 回退只需把 {@code finflow.batch.worker} 切回 backend。</p>
 *
 * <p>库结构迁移由 backend 的 Flyway 统一执行（{@code spring.flyway.enabled=false}，
 * 见 application-batch.yaml）；对账表在 finflow 主库，未拆库，匹配 SQL 口径与单体一致。</p>
 */
@SpringBootApplication(scanBasePackages = {
        "org.example.finflowbackend.reconciliation.task",
        // 对账异常事件仍在事务提交后发布（S33 通知源），复用同一事件发布器
        "org.example.finflowbackend.common.event",
        "org.example.finflowbackend.common.trace",
        "org.example.finflowbatch"
})
@Import({MybatisPlusConfig.class, ReconciliationMatchService.class,
        PaymentTransactionService.class, ConfigServiceImpl.class})
@MapperScan({"org.example.finflowbackend.reconciliation.mapper",
        "org.example.finflowbackend.channel.mapper",
        "org.example.finflowbackend.system.mapper"})
@EnableScheduling
public class BatchServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(BatchServiceApplication.class, args);
    }
}
