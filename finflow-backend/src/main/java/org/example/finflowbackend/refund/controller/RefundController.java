package org.example.finflowbackend.refund.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.example.finflowbackend.auth.web.CurrentUserHolder;
import org.example.finflowbackend.common.api.ApiResponse;
import org.example.finflowbackend.common.api.PageResponse;
import org.example.finflowbackend.common.idempotent.Idempotent;
import org.example.finflowbackend.refund.dto.RefundCreateRequest;
import org.example.finflowbackend.refund.dto.RefundOrderDetailResponse;
import org.example.finflowbackend.refund.dto.RefundOrderResponse;
import org.example.finflowbackend.refund.service.RefundService;
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
 * 退款接口。权限编码与 sys_permission 初始化数据（V18）一一对应。
 */
@RestController
@Tag(name = "退款")
@RequestMapping("/api/v1/refunds")
public class RefundController {

    private final RefundService refundService;

    public RefundController(RefundService refundService) {
        this.refundService = refundService;
    }

    @PostMapping
    @Idempotent
    @PreAuthorize("hasAuthority('refund:order:create')")
    public ApiResponse<RefundOrderResponse> create(@Valid @RequestBody RefundCreateRequest request) {
        return ApiResponse.ok(refundService.create(request, CurrentUserHolder.current().username()));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('refund:order:list')")
    public ApiResponse<PageResponse<RefundOrderResponse>> page(@RequestParam(defaultValue = "1") long page,
                                                               @RequestParam(defaultValue = "20") long size,
                                                               @RequestParam(required = false) Long orderId,
                                                               @RequestParam(required = false) Long merchantId,
                                                               @RequestParam(required = false) String refundNo,
                                                               @RequestParam(required = false) String status,
                                                               @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                                                               @RequestParam(required = false) LocalDateTime createdFrom,
                                                               @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                                                               @RequestParam(required = false) LocalDateTime createdTo) {
        return ApiResponse.ok(refundService.page(page, size, orderId, merchantId, refundNo, status,
                createdFrom, createdTo));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('refund:order:query')")
    public ApiResponse<RefundOrderDetailResponse> detail(@PathVariable Long id) {
        return ApiResponse.ok(refundService.getDetail(id));
    }
}
