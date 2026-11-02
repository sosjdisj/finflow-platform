package org.example.finflowbackend.channel.api;

import java.util.List;
import java.util.Map;

/**
 * 渠道交互留痕查询的对外能力。
 */
public interface PaymentTransactionApi {

    /** 实际收款渠道码（订单最新一条 PAYMENT 交互行）；无记录返回 null。 */
    String findChannelCode(Long orderId);

    /** 实际支付渠道单号（订单最新一条 PAYMENT 交互行）；无记录返回 null。 */
    String findPaymentChannelOrderNo(Long orderId);

    /** 按订单查渠道交互留痕（时间正序），供支付订单详情展示。 */
    List<PaymentTransactionSnapshot> listByOrder(Long orderId);

    /**
     * 批量查订单在指定渠道的首条交互渠道单号（S28 对账 MISSING_IN_CHANNEL 用）。
     * 返回的 key 即「该订单走过本渠道」，value 为该首条交互的渠道单号（渠道未返回时可为 null）。
     */
    Map<Long, String> findChannelOrderNos(List<Long> orderIds, String channelCode);
}
