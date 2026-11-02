package org.example.finflowbackend.channel.service;

import org.example.finflowbackend.channel.api.ChannelRequest;
import org.example.finflowbackend.channel.api.ChannelResult;

/**
 * 统一支付渠道适配接口（策略模式）：新增渠道只需实现本接口并注册为 Spring Bean，
 * 再往 payment_channel 表加一行配置即可接入，核心逻辑零改动。
 * 渠道单号由渠道生成；受理类交互先返回「受理中」，异步结果由渠道回调。
 *
 * <p>M08 起本接口是 channel 模块内部 SPI，不再对外跨模块暴露；
 * 进程边界由 {@link org.example.finflowbackend.channel.api.ChannelOperationsApi} 承担。</p>
 */
public interface ChannelAdapter {

    /** 渠道编码，与 payment_channel.channel_code 一致。 */
    String channelCode();

    /** 创建支付：受理返回渠道单号，状态「受理中」。 */
    ChannelResult createPayment(ChannelRequest request);

    /** 查询支付结果。 */
    ChannelResult queryPayment(ChannelRequest request);

    /** 发起退款。 */
    ChannelResult refund(ChannelRequest request);

    /** 查询退款结果。 */
    ChannelResult queryRefund(ChannelRequest request);
}
