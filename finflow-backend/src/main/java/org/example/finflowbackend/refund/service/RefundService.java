package org.example.finflowbackend.refund.service;

import org.example.finflowbackend.common.api.PageResponse;
import org.example.finflowbackend.refund.dto.RefundCreateRequest;
import org.example.finflowbackend.refund.dto.RefundOrderDetailResponse;
import org.example.finflowbackend.refund.dto.RefundOrderResponse;

import java.time.LocalDateTime;

/**
 * 退款单管理。发起退款与渠道回调的编排见 {@link RefundServiceImpl}；
 * 防超退的行锁与额度占用在 payment 模块（PaymentOrderService），refund 不直接读支付表。
 */
public interface RefundService {

    /**
     * 发起退款：占用可退额度（行锁防并发超退）→ 落退款单 → 渠道受理。
     * 渠道拒绝 / 路由失败时退款单终态化 FAILED 并释放额度，异常上抛。
     */
    RefundOrderResponse create(RefundCreateRequest request, String operator);

    /** 管理端分页查询：金额条件不涉及，时间条件为创建时间闭区间；条件均为可选。 */
    PageResponse<RefundOrderResponse> page(long page, long size, Long orderId, Long merchantId,
                                           String refundNo, String status,
                                           LocalDateTime createdFrom, LocalDateTime createdTo);

    /** 退款详情：退款单 + 状态留痕。 */
    RefundOrderDetailResponse getDetail(Long id);
}
