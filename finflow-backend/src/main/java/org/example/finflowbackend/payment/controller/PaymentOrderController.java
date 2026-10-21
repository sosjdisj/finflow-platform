package org.example.finflowbackend.payment.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.example.finflowbackend.common.api.ApiResponse;
import org.example.finflowbackend.common.api.PageResponse;
import org.example.finflowbackend.common.idempotent.Idempotent;
import org.example.finflowbackend.payment.dto.PaymentOrderCreateRequest;
import org.example.finflowbackend.payment.dto.PaymentOrderDetailResponse;
import org.example.finflowbackend.payment.dto.PaymentOrderResponse;
import org.example.finflowbackend.payment.service.PaymentOrderService;
import org.example.finflowbackend.payment.service.PaymentProcessService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

/**
 * 支付订单接口。权限编码与 sys_permission 初始化数据一一对应。
 */
@RestController
@Tag(name = "支付订单")
@RequestMapping("/api/v1/payments/orders")
public class PaymentOrderController {

    private final PaymentOrderService paymentOrderService;
    private final PaymentProcessService paymentProcessService;

    public PaymentOrderController(PaymentOrderService paymentOrderService, PaymentProcessService paymentProcessService) {
        this.paymentOrderService = paymentOrderService;
        this.paymentProcessService = paymentProcessService;
    }

    @PostMapping
    @Idempotent
    @PreAuthorize("hasAuthority('payment:order:create')")
    public ApiResponse<PaymentOrderResponse> create(@Valid @RequestBody PaymentOrderCreateRequest request) {
        return ApiResponse.ok(paymentProcessService.createAndProcess(request));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('payment:order:list')")
    public ApiResponse<PageResponse<PaymentOrderResponse>> page(@RequestParam(defaultValue = "1") long page,
                                                                @RequestParam(defaultValue = "20") long size,
                                                                @RequestParam(required = false) Long merchantId,
                                                                @RequestParam(required = false) String status,
                                                                @RequestParam(required = false) String keyword,
                                                                @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                                                                @RequestParam(required = false) LocalDateTime createdFrom,
                                                                @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                                                                @RequestParam(required = false) LocalDateTime createdTo,
                                                                @RequestParam(required = false) Long minAmount,
                                                                @RequestParam(required = false) Long maxAmount) {
        return ApiResponse.ok(paymentOrderService.page(page, size, merchantId, status, keyword,
                createdFrom, createdTo, minAmount, maxAmount));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('payment:order:query')")
    public ApiResponse<PaymentOrderDetailResponse> detail(@PathVariable Long id) {
        return ApiResponse.ok(paymentOrderService.getDetail(id));
    }
}
