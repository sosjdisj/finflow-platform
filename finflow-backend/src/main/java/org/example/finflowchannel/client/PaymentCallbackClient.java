package org.example.finflowchannel.client;

import org.example.finflowbackend.payment.api.ChannelCallbackCommand;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * 回调转发客户端（M08 / M14）：渠道服务验签解析渠道通知后，把已验签结果转给 backend 的
 * {@code POST /api/v1/internal/channel/callbacks}，由 payment 落状态。
 * M14 起按服务名 finflow-backend 走 Nacos 服务发现 + 客户端负载均衡，不再写死 URL；
 * 本机没有注册中心时可用 {@code spring.cloud.openfeign.client.config...} 或回退到 local 模式。
 */
@FeignClient(name = "finflow-backend", contextId = "paymentCallback", path = "/api/v1/internal/channel")
public interface PaymentCallbackClient {

    @PostMapping("/callbacks")
    boolean handle(@RequestBody ChannelCallbackCommand command);
}
