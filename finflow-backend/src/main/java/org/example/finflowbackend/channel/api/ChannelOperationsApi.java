package org.example.finflowbackend.channel.api;

/**
 * 渠道操作对外能力（M08 进程边界）：受理 / 查询 / 退款 / 退款查询。
 *
 * <p>调用方（payment / refund）只依赖本接口，不感知渠道是进程内实现还是独立服务：
 * {@code finflow.channel.mode=local} 由 channel 模块实现，{@code remote} 由 Feign 客户端实现。</p>
 *
 * <p>契约：业务失败（无可用渠道、渠道拒绝）以 {@code accepted=false} + message 返回，不抛异常；
 * 基础设施失败（渠道服务不可用 / 熔断降级）抛 {@link ChannelErrorCode#CHANNEL_SERVICE_UNAVAILABLE}。</p>
 */
public interface ChannelOperationsApi {

    /** 创建支付：受理返回渠道单号，状态「受理中」。 */
    ChannelResult createPayment(ChannelRequest request);

    /** 查询支付结果。 */
    ChannelResult queryPayment(ChannelRequest request);

    /** 发起退款。 */
    ChannelResult refund(ChannelRequest request);

    /** 查询退款结果。 */
    ChannelResult queryRefund(ChannelRequest request);
}
