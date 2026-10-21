package org.example.fakechannel;

import org.example.fakechannel.config.FakeChannelProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * 第三方支付渠道模拟器（独立进程，端口 8090）。
 *
 * <p>它模拟支付宝/微信风格的第三方渠道：对外暴露渠道协议接口，收到受理后延迟发送（可重复）异步通知，
 * 并提供对账单 CSV。账本放内存，不连 MySQL。</p>
 */
@SpringBootApplication
@EnableConfigurationProperties(FakeChannelProperties.class)
public class FakeChannelApplication {

    public static void main(String[] args) {
        SpringApplication.run(FakeChannelApplication.class, args);
    }
}
