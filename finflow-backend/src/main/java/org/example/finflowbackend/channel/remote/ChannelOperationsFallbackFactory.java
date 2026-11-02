package org.example.finflowbackend.channel.remote;

import org.example.finflowbackend.channel.api.ChannelErrorCode;
import org.example.finflowbackend.channel.api.ChannelRequest;
import org.example.finflowbackend.channel.api.ChannelResult;
import org.example.finflowbackend.common.exception.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

/**
 * Sentinel 降级（M08）：渠道服务超时 / 熔断 / 不可达时快速失败，
 * 统一抛 {@link ChannelErrorCode#CHANNEL_SERVICE_UNAVAILABLE}，由支付编排把订单终态化。
 * 备用渠道切换属业务路由策略，本步不做（报告允许「快速失败」）。
 */
@Component
@ConditionalOnProperty(name = "finflow.channel.mode", havingValue = "remote")
public class ChannelOperationsFallbackFactory implements FallbackFactory<ChannelOperationsClient> {

    private static final Logger log = LoggerFactory.getLogger(ChannelOperationsFallbackFactory.class);

    @Override
    public ChannelOperationsClient create(Throwable cause) {
        log.warn("渠道服务调用失败，触发降级: {}", cause.toString());
        return new ChannelOperationsClient() {
            @Override
            public ChannelResult createPayment(ChannelRequest request) {
                throw unavailable();
            }

            @Override
            public ChannelResult queryPayment(ChannelRequest request) {
                throw unavailable();
            }

            @Override
            public ChannelResult refund(ChannelRequest request) {
                throw unavailable();
            }

            @Override
            public ChannelResult queryRefund(ChannelRequest request) {
                throw unavailable();
            }
        };
    }

    private static BusinessException unavailable() {
        return new BusinessException(ChannelErrorCode.CHANNEL_SERVICE_UNAVAILABLE);
    }
}
