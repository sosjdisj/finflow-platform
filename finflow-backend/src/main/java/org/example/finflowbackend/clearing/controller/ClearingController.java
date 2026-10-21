package org.example.finflowbackend.clearing.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import org.example.finflowbackend.clearing.dto.ClearingOrderDetailResponse;
import org.example.finflowbackend.clearing.dto.ClearingOrderResponse;
import org.example.finflowbackend.clearing.service.ClearingQueryService;
import org.example.finflowbackend.common.api.ApiResponse;
import org.example.finflowbackend.common.api.PageResponse;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * 清分查询接口。权限编码与 sys_permission 初始化数据（V23）一一对应。
 */
@RestController
@Tag(name = "清分")
@RequestMapping("/api/v1/clearings")
public class ClearingController {

    private final ClearingQueryService queryService;

    public ClearingController(ClearingQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('clearing:clearing:list')")
    public ApiResponse<PageResponse<ClearingOrderResponse>> page(@RequestParam(defaultValue = "1") long page,
                                                                 @RequestParam(defaultValue = "20") long size,
                                                                 @RequestParam(required = false) String platformOrderNo,
                                                                 @RequestParam(required = false) Long merchantId,
                                                                 @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                                                                 @RequestParam(required = false) LocalDate settleDate) {
        return ApiResponse.ok(queryService.page(page, size, platformOrderNo, merchantId, settleDate));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('clearing:clearing:query')")
    public ApiResponse<ClearingOrderDetailResponse> detail(@PathVariable Long id) {
        return ApiResponse.ok(queryService.getDetail(id));
    }
}
