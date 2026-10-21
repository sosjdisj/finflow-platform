package org.example.finflowbackend.payment.channel;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.example.finflowbackend.payment.PaymentStatus;
import org.example.finflowbackend.payment.entity.PaymentCallback;
import org.example.finflowbackend.payment.mapper.PaymentCallbackMapper;
import org.example.finflowbackend.payment.service.PaymentOrderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

/**
 * 渠道回调处理：渠道异步通知到达后，回调落 payment_callback（只增不改）+ 把渠道结果落到订单状态。
 * 支付：成功 PENDING → PROCESSING → PAID（两跳各留痕）；失败 PENDING → FAILED。
 * 退款：记回调流水后委托 RefundResultHandler（refund 模块实现）；
 * ObjectProvider 懒取避免 payment → refund 的 Bean 构造依赖环。
 * 渠道可能重复通知，按 channelCode + 渠道单号先做去重（已处理过直接忽略），保证幂等。
 * 状态校验与留痕集中在各 Service，这里不碰状态机。
 */
@Component
public class ChannelCallbackHandler {

    private static final Logger log = LoggerFactory.getLogger(ChannelCallbackHandler.class);

    private final PaymentOrderService paymentOrderService;
    private final PaymentCallbackMapper callbackMapper;
    private final ObjectMapper objectMapper;
    private final ObjectProvider<RefundResultHandler> refundResultHandler;

    public ChannelCallbackHandler(PaymentOrderService paymentOrderService,
                                  PaymentCallbackMapper callbackMapper, ObjectMapper objectMapper,
                                  ObjectProvider<RefundResultHandler> refundResultHandler) {
        this.paymentOrderService = paymentOrderService;
        this.callbackMapper = callbackMapper;
        this.objectMapper = objectMapper;
        this.refundResultHandler = refundResultHandler;
    }

    public void onSuccess(Long orderId, String channelCode, String channelOrderNo) {
        if (alreadyProcessed(channelCode, channelOrderNo)) {
            return;
        }
        recordCallback(orderId, channelCode, channelOrderNo, "SUCCESS", "渠道回调支付成功");
        String operator = "CHANNEL:" + channelCode;
        paymentOrderService.changeStatus(orderId, PaymentStatus.PROCESSING.name(),
                "渠道处理中: " + channelOrderNo, operator);
        paymentOrderService.changeStatus(orderId, PaymentStatus.PAID.name(),
                "渠道回调支付成功: " + channelOrderNo, operator);
    }

    public void onFailure(Long orderId, String channelCode, String channelOrderNo, String message) {
        if (alreadyProcessed(channelCode, channelOrderNo)) {
            return;
        }
        recordCallback(orderId, channelCode, channelOrderNo, "FAILED", message);
        paymentOrderService.changeStatus(orderId, PaymentStatus.FAILED.name(),
                "渠道回调支付失败: " + channelOrderNo + " " + message, "CHANNEL:" + channelCode);
    }

    /** 退款成功回调：记回调流水后委托 refund 模块终态化并发布资金事件。 */
    public void onRefundSuccess(Long orderId, String channelCode, String channelRefundNo, String refundNo) {
        if (alreadyProcessed(channelCode, channelRefundNo)) {
            return;
        }
        recordRefundCallback(orderId, channelCode, channelRefundNo, refundNo,
                "REFUND_SUCCESS", "渠道回调退款成功");
        RefundResultHandler handler = refundResultHandler.getIfAvailable();
        if (handler != null) {
            handler.onResult(orderId, channelCode, refundNo, channelRefundNo, true, "退款成功");
        }
    }

    /** 退款失败回调：记回调流水后委托 refund 模块释放额度终态化。 */
    public void onRefundFailure(Long orderId, String channelCode, String channelRefundNo,
                                String refundNo, String message) {
        if (alreadyProcessed(channelCode, channelRefundNo)) {
            return;
        }
        recordRefundCallback(orderId, channelCode, channelRefundNo, refundNo, "REFUND_FAILED", message);
        RefundResultHandler handler = refundResultHandler.getIfAvailable();
        if (handler != null) {
            handler.onResult(orderId, channelCode, refundNo, channelRefundNo, false, message);
        }
    }

    /** 渠道重复通知去重：同一 channelCode + 渠道单号已有回调流水即视为已处理。 */
    private boolean alreadyProcessed(String channelCode, String channelOrderNo) {
        Long count = callbackMapper.selectCount(Wrappers.<PaymentCallback>query()
                .eq("channel_code", channelCode)
                .eq("channel_order_no", channelOrderNo));
        if (count != null && count > 0) {
            log.info("渠道重复通知，忽略: channelCode={}, channelOrderNo={}", channelCode, channelOrderNo);
            return true;
        }
        return false;
    }

    /** 回调流水只增不改；与状态推进分离，流水写失败不影响状态（各自独立）。 */
    private void recordCallback(Long orderId, String channelCode, String channelOrderNo,
                                String result, String message) {
        PaymentCallback callback = new PaymentCallback();
        callback.setOrderId(orderId);
        callback.setChannelCode(channelCode);
        callback.setChannelOrderNo(channelOrderNo);
        callback.setResult(result);
        callback.setPayload(objectMapper.writeValueAsString(
                Map.of("channelOrderNo", channelOrderNo, "message", message)));
        callbackMapper.insert(callback);
    }

    private void recordRefundCallback(Long orderId, String channelCode, String channelRefundNo,
                                      String refundNo, String result, String message) {
        PaymentCallback callback = new PaymentCallback();
        callback.setOrderId(orderId);
        callback.setChannelCode(channelCode);
        callback.setChannelOrderNo(channelRefundNo);
        callback.setResult(result);
        callback.setPayload(objectMapper.writeValueAsString(
                Map.of("refundNo", refundNo, "channelRefundNo", channelRefundNo, "message", message)));
        callbackMapper.insert(callback);
    }
}
