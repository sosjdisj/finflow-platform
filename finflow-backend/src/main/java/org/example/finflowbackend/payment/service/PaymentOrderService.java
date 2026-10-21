package org.example.finflowbackend.payment.service;

import org.example.finflowbackend.common.api.PageResponse;
import org.example.finflowbackend.payment.dto.PaymentOrderCreateRequest;
import org.example.finflowbackend.payment.dto.PaymentOrderDetailResponse;
import org.example.finflowbackend.payment.dto.PaymentOrderResponse;
import org.example.finflowbackend.payment.entity.PaymentOrder;

import java.time.LocalDateTime;

/**
 * 支付订单管理。状态推进（渠道回调、退款等）由后续步骤在此接口上扩展。
 */
public interface PaymentOrderService {

    PaymentOrderResponse create(PaymentOrderCreateRequest request);

    PaymentOrderDetailResponse getDetail(Long id);

    /** 读取支付订单（退款编排取渠道要素用）。 */
    PaymentOrderResponse get(Long id);

    /** 按平台订单号取订单 ID（渠道异步通知按平台单号定位订单用）；不存在返回 null。 */
    Long findIdByPlatformOrderNo(String platformOrderNo);

    /**
     * 管理端分页查询。金额条件单位为分，时间条件为创建时间闭区间；条件均为可选。
     */
    PageResponse<PaymentOrderResponse> page(long page, long size, Long merchantId, String status, String keyword,
                                            LocalDateTime createdFrom, LocalDateTime createdTo,
                                            Long minAmount, Long maxAmount);

    /**
     * 状态推进（内部编排用，不对外暴露接口）：校验合法转换、更新状态并写留痕，同事务。
     * 非法转换抛 ILLEGAL_STATUS_TRANSITION。
     */
    void changeStatus(Long orderId, String toStatus, String reason, String operator);

    /**
     * S21 退款防超退：行锁读订单 → 校验状态（PAID/REFUNDING）与剩余可退 → 占用额度
     * （refunded_amount 累加），首笔退款联动 PAID → REFUNDING。
     * 返回行锁读出的订单快照，供退款单落冗余字段。
     * 状态不符 / 超退分别抛 refund 模块的 ORDER_NOT_REFUNDABLE / REFUND_AMOUNT_EXCEED
     * （错误码属退款语义；校验放进行锁内，保证校验与占用原子）。
     */
    PaymentOrder reserveRefundQuota(Long orderId, long refundAmount, String operator);

    /** 退款失败释放占用额度（行锁内回退 refunded_amount）。 */
    void releaseRefundQuota(Long orderId, long refundAmount);

    /** 退款成功后调用：行锁内判断累计退满则 REFUNDING → REFUNDED，未退满保持不动。 */
    void markFullyRefundedIfNeeded(Long orderId, String refundNo, String operator);
}