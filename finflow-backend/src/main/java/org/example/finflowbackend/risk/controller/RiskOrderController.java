package org.example.finflowbackend.risk.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import org.example.finflowbackend.auth.web.CurrentUserHolder;
import org.example.finflowbackend.common.api.ApiResponse;
import org.example.finflowbackend.risk.dto.ReviewHandleRequest;
import org.example.finflowbackend.risk.service.RiskReviewService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 风控 REVIEW 订单人工处理接口：通过（回 PENDING 并渠道路由）/ 拒绝（FAILED）。
 * 风险订单列表复用支付订单分页接口（status=REVIEW 筛选），此处只提供处理动作。
 * 权限编码与 V27 初始化数据一一对应。
 */
@RestController
@Tag(name = "风控订单")
@RequestMapping("/api/v1/risk/orders")
public class RiskOrderController {

    private final RiskReviewService reviewService;

    public RiskOrderController(RiskReviewService reviewService) {
        this.reviewService = reviewService;
    }

    /** 人工通过：订单回 PENDING 并直接渠道路由，不重查风控。 */
    @PostMapping("/{orderId}/approve")
    @PreAuthorize("hasAuthority('risk:order:handle')")
    public ApiResponse<Void> approve(@PathVariable Long orderId,
                                     @RequestBody(required = false) ReviewHandleRequest request) {
        reviewService.approve(orderId, CurrentUserHolder.current().username(),
                request == null ? null : request.remark());
        return ApiResponse.ok(null);
    }

    /** 人工拒绝：订单置 FAILED。 */
    @PostMapping("/{orderId}/reject")
    @PreAuthorize("hasAuthority('risk:order:handle')")
    public ApiResponse<Void> reject(@PathVariable Long orderId,
                                    @RequestBody(required = false) ReviewHandleRequest request) {
        reviewService.reject(orderId, CurrentUserHolder.current().username(),
                request == null ? null : request.remark());
        return ApiResponse.ok(null);
    }
}
