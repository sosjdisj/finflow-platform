package org.example.finflowchannel;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * 渠道服务启动类（M08）：与 backend 同源（channel 模块代码零复制），打包成独立可执行 jar
 * （{@code -channel.jar}）作为独立进程运行（端口 8092，Nacos 注册名 finflow-channel）。
 *
 * <p>只装配 channel 模块（渠道配置 / 适配器 / 回调验签）+ 本服务的回调 Feign 转发，
 * 不扫描 payment / refund / account / ledger 等资金主链路；Flyway 迁移仍由 backend 执行
 * （{@code spring.flyway.enabled=false}，见 application-channel.yaml）。</p>
 */
@SpringBootApplication(scanBasePackages = {"org.example.finflowbackend.channel",
        "org.example.finflowbackend.common.tunable", "org.example.finflowchannel"})
@ConfigurationPropertiesScan("org.example.finflowbackend.channel.config")
@MapperScan("org.example.finflowbackend.channel.mapper")
@EnableFeignClients(basePackages = "org.example.finflowchannel.client")
public class ChannelServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ChannelServiceApplication.class, args);
    }
}
