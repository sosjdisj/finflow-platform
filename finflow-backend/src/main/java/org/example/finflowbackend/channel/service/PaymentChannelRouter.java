package org.example.finflowbackend.channel.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.example.finflowbackend.channel.api.ChannelErrorCode;
import org.example.finflowbackend.channel.entity.PaymentChannel;
import org.example.finflowbackend.channel.mapper.PaymentChannelMapper;
import org.example.finflowbackend.common.exception.BusinessException;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 渠道路由器（策略模式）：按支付方式在启用的渠道配置中匹配，
 * 返回对应渠道实现。新增渠道注册 Bean + 配置行即可，无需改本类。
 */
@Component
public class PaymentChannelRouter implements ChannelRouter {

    private final PaymentChannelMapper channelMapper;
    private final Map<String, ChannelAdapter> channelsByCode;

    public PaymentChannelRouter(PaymentChannelMapper channelMapper, List<ChannelAdapter> channels) {
        this.channelMapper = channelMapper;
        this.channelsByCode = channels.stream()
                .collect(Collectors.toUnmodifiableMap(ChannelAdapter::channelCode, Function.identity()));
    }

    /** 按支付方式路由；无启用的支持渠道抛 CHANNEL_NOT_FOUND。 */
    @Override
    public ChannelAdapter route(String paymentMethod) {
        List<PaymentChannel> enabled = channelMapper.selectList(
                Wrappers.<PaymentChannel>query().eq("enabled", true));
        for (PaymentChannel config : enabled) {
            boolean supported = Arrays.stream(config.getPaymentMethods().split(","))
                    .map(String::trim)
                    .anyMatch(paymentMethod::equals);
            if (supported) {
                ChannelAdapter channel = channelsByCode.get(config.getChannelCode());
                if (channel != null) {
                    return channel;
                }
            }
        }
        throw new BusinessException(ChannelErrorCode.CHANNEL_NOT_FOUND);
    }
}
