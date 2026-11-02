package org.example.finflowbackend.channel.service;

/**
 * 渠道路由（模块内部能力）：按支付方式返回启用的渠道适配器。
 * 路由读的是渠道配置表，调用方不直接接触；跨进程入口见
 * {@link org.example.finflowbackend.channel.api.ChannelOperationsApi}。
 */
public interface ChannelRouter {

    /** 按支付方式路由；无启用的支持渠道抛 {@link org.example.finflowbackend.channel.api.ChannelErrorCode#CHANNEL_NOT_FOUND}。 */
    ChannelAdapter route(String paymentMethod);
}
