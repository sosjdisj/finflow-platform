package org.example.finflowbackend.payment.controller;

import org.example.finflowbackend.payment.api.ChannelCallbackApi;
import org.example.finflowbackend.payment.api.ChannelCallbackCommand;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 渠道回调内部端点（M08）：渠道服务验签并解析渠道报文后，经 Feign 把已验签结果转发给 payment。
 * 只在 backend 进程注册（payment 模块不被渠道服务扫描）；路径归入 internal，由网络边界
 * （compose 内网 / M13 网关）屏蔽，不对公网与运营后台暴露。
 * 返回平台订单是否存在：false 表示订单不存在，渠道服务据此给渠道方回业务失败。
 */
@RestController
@RequestMapping("/api/v1/internal/channel")
public class ChannelCallbackInternalController {

    private final ChannelCallbackApi callbackApi;

    public ChannelCallbackInternalController(ChannelCallbackApi callbackApi) {
        this.callbackApi = callbackApi;
    }

    @PostMapping("/callbacks")
    public boolean handle(@RequestBody ChannelCallbackCommand command) {
        return callbackApi.handle(command);
    }
}
