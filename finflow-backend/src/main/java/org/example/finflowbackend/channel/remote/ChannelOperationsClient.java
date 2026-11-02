package org.example.finflowbackend.channel.remote;

import org.example.finflowbackend.channel.api.ChannelOperationsApi;
import org.example.finflowbackend.channel.api.ChannelRequest;
import org.example.finflowbackend.channel.api.ChannelResult;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * 远程渠道操作客户端（M08）：由 {@link ChannelRemoteConfig} 在
 * {@code finflow.channel.mode=remote} 时注册。服务名为渠道服务在 Nacos 的注册名；
 * Sentinel 包裹后由 {@link ChannelOperationsFallbackFactory} 提供熔断 / 超时降级（快速失败）。
 */
@FeignClient(name = "finflow-channel", contextId = "channelOperations",
        path = "/api/v1/internal/channel",
        fallbackFactory = ChannelOperationsFallbackFactory.class)
public interface ChannelOperationsClient extends ChannelOperationsApi {

    @Override
    @PostMapping("/payments")
    ChannelResult createPayment(@RequestBody ChannelRequest request);

    @Override
    @PostMapping("/payments/query")
    ChannelResult queryPayment(@RequestBody ChannelRequest request);

    @Override
    @PostMapping("/refunds")
    ChannelResult refund(@RequestBody ChannelRequest request);

    @Override
    @PostMapping("/refunds/query")
    ChannelResult queryRefund(@RequestBody ChannelRequest request);
}
