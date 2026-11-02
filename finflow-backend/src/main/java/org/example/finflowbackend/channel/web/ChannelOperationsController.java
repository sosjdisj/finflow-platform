package org.example.finflowbackend.channel.web;

import org.example.finflowbackend.channel.api.ChannelOperationsApi;
import org.example.finflowbackend.channel.api.ChannelRequest;
import org.example.finflowbackend.channel.api.ChannelResult;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 渠道服务内部操作端点（M08）：只在渠道服务进程注册（{@code finflow.channel.provider=true}）。
 * backend 经 Feign 调用；不对公网 / 运营后台暴露（M13 网关会屏蔽 internal 路径）。
 * 入参出参均为 channel.api 的契约类型，不携带协议报文与签名。
 */
@RestController
@RequestMapping("/api/v1/internal/channel")
@ConditionalOnProperty(name = "finflow.channel.provider", havingValue = "true")
public class ChannelOperationsController {

    private final ChannelOperationsApi operations;

    public ChannelOperationsController(ChannelOperationsApi operations) {
        this.operations = operations;
    }

    @PostMapping("/payments")
    public ChannelResult createPayment(@RequestBody ChannelRequest request) {
        return operations.createPayment(request);
    }

    @PostMapping("/payments/query")
    public ChannelResult queryPayment(@RequestBody ChannelRequest request) {
        return operations.queryPayment(request);
    }

    @PostMapping("/refunds")
    public ChannelResult refund(@RequestBody ChannelRequest request) {
        return operations.refund(request);
    }

    @PostMapping("/refunds/query")
    public ChannelResult queryRefund(@RequestBody ChannelRequest request) {
        return operations.queryRefund(request);
    }
}
