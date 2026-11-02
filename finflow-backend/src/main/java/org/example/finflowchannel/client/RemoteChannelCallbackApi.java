package org.example.finflowchannel.client;

import org.example.finflowbackend.payment.api.ChannelCallbackApi;
import org.example.finflowbackend.payment.api.ChannelCallbackCommand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 渠道服务内的回调入口实现（M08）：{@link org.example.finflowbackend.channel.web.ChannelNotifyController}
 * 依赖 {@link ChannelCallbackApi}，backend 进程里由 payment 的 ChannelCallbackService 提供，
 * 渠道服务进程里由本类经 Feign 转给 backend。
 *
 * <p>转发失败按「未处理」返回（false），渠道服务对渠道方回业务失败让其重试；
 * 回调本身的幂等由 payment 侧去重保证。</p>
 */
@Component
public class RemoteChannelCallbackApi implements ChannelCallbackApi {

    private static final Logger log = LoggerFactory.getLogger(RemoteChannelCallbackApi.class);

    private final PaymentCallbackClient client;

    public RemoteChannelCallbackApi(PaymentCallbackClient client) {
        this.client = client;
    }

    @Override
    public boolean handle(ChannelCallbackCommand command) {
        try {
            return client.handle(command);
        } catch (RuntimeException e) {
            log.warn("渠道回调转发 backend 失败: platformOrderNo={} err={}", command.platformOrderNo(), e.toString());
            return false;
        }
    }
}
