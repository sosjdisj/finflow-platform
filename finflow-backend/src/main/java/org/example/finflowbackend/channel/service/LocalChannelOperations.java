package org.example.finflowbackend.channel.service;

import org.example.finflowbackend.channel.api.ChannelErrorCode;
import org.example.finflowbackend.channel.api.ChannelOperationsApi;
import org.example.finflowbackend.channel.api.ChannelRequest;
import org.example.finflowbackend.channel.api.ChannelResult;
import org.example.finflowbackend.common.exception.BusinessException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.function.BiFunction;

/**
 * 进程内渠道实现（M08 回退路径）：默认模式，路由 + 适配器都在本进程。
 * 渠道服务拆出后，把 {@code finflow.channel.mode} 切成 remote 即可改走 Feign，代码无需回滚。
 */
@Component
@ConditionalOnProperty(name = "finflow.channel.mode", havingValue = "local", matchIfMissing = true)
public class LocalChannelOperations implements ChannelOperationsApi {

    private final ChannelRouter channelRouter;

    public LocalChannelOperations(ChannelRouter channelRouter) {
        this.channelRouter = channelRouter;
    }

    @Override
    public ChannelResult createPayment(ChannelRequest request) {
        return invoke(request, ChannelAdapter::createPayment);
    }

    @Override
    public ChannelResult queryPayment(ChannelRequest request) {
        return invoke(request, ChannelAdapter::queryPayment);
    }

    @Override
    public ChannelResult refund(ChannelRequest request) {
        return invoke(request, ChannelAdapter::refund);
    }

    @Override
    public ChannelResult queryRefund(ChannelRequest request) {
        return invoke(request, ChannelAdapter::queryRefund);
    }

    /** 路由失败按业务失败返回（accepted=false），保持与远程实现的契约一致。 */
    private ChannelResult invoke(ChannelRequest request,
                                 BiFunction<ChannelAdapter, ChannelRequest, ChannelResult> action) {
        try {
            return action.apply(channelRouter.route(request.paymentMethod()), request);
        } catch (BusinessException e) {
            if (e.getErrorCode() == ChannelErrorCode.CHANNEL_NOT_FOUND) {
                return new ChannelResult(null, false, null, null, e.getMessage());
            }
            throw e;
        }
    }
}
