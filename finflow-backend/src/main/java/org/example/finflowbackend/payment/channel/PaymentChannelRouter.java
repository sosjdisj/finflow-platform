package org.example.finflowbackend.payment.channel;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.example.finflowbackend.common.exception.BusinessException;
import org.example.finflowbackend.payment.PaymentErrorCode;
import org.example.finflowbackend.payment.mapper.PaymentChannelMapper;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 渠道路由器（策略模式）：按支付方式在启用的渠道配置中匹配，
 * 返回对应渠道实现。新增渠道注册 Bean + 配置行即可，无需改本类。
 * 注：路由方法用 var 推断配置实体类型，避免与接口同名实体在此文件显式出现。
 */
@Component
public class PaymentChannelRouter {

    private final PaymentChannelMapper channelMapper;
    private final Map<String, PaymentChannel> channelsByCode;

    public PaymentChannelRouter(PaymentChannelMapper channelMapper, List<PaymentChannel> channels) {
        this.channelMapper = channelMapper;
        this.channelsByCode = channels.stream()
                .collect(Collectors.toUnmodifiableMap(PaymentChannel::channelCode, Function.identity()));
    }

    /** 按支付方式路由；无启用的支持渠道抛 CHANNEL_NOT_FOUND。 */
    public PaymentChannel route(String paymentMethod) {
        // 配置实体与接口同名（payment_channel 表实体），此处仅出现一次，用全限定名区分
        var enabled = channelMapper.selectList(Wrappers
                .<org.example.finflowbackend.payment.entity.PaymentChannel>query().eq("enabled", true));
        for (var config : enabled) {
            boolean supported = Arrays.stream(config.getPaymentMethods().split(","))
                    .map(String::trim)
                    .anyMatch(paymentMethod::equals);
            if (supported) {
                PaymentChannel channel = channelsByCode.get(config.getChannelCode());
                if (channel != null) {
                    return channel;
                }
            }
        }
        throw new BusinessException(PaymentErrorCode.CHANNEL_NOT_FOUND);
    }
}
