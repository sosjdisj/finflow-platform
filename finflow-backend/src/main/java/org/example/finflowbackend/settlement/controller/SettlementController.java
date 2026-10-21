package org.example.finflowbackend.settlement.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import org.example.finflowbackend.audit.annotation.Audited;
import org.example.finflowbackend.auth.web.CurrentUserHolder;
import org.example.finflowbackend.common.api.ApiResponse;
import org.example.finflowbackend.common.api.PageResponse;
import org.example.finflowbackend.settlement.dto.SettlementOrderDetailResponse;
import org.example.finflowbackend.settlement.dto.SettlementOrderResponse;
import org.example.finflowbackend.settlement.service.SettlementExecuteService;
import org.example.finflowbackend.settlement.service.SettlementQueryService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * 结算接口。权限编码与 sys_permission 初始化数据（V22）一一对应。
 */
@RestController
@Tag(name = "结算")
@RequestMapping("/api/v1/settlements")
public class SettlementController {

    private final SettlementQueryService queryService;
    private final SettlementExecuteService executeService;

    public SettlementController(SettlementQueryService queryService, SettlementExecuteService executeService) {
        this.queryService = queryService;
        this.executeService = executeService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('settlement:settle:list')")
    public ApiResponse<PageResponse<SettlementOrderResponse>> page(@RequestParam(defaultValue = "1") long page,
                                                                   @RequestParam(defaultValue = "20") long size,
                                                                   @RequestParam(required = false) String settleNo,
                                                                   @RequestParam(required = false) Long merchantId,
                                                                   @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                                                                   @RequestParam(required = false) LocalDate settleDate,
                                                                   @RequestParam(required = false) String status) {
        return ApiResponse.ok(queryService.page(page, size, settleNo, merchantId, settleDate, status));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('settlement:settle:query')")
    public ApiResponse<SettlementOrderDetailResponse> detail(@PathVariable Long id) {
        return ApiResponse.ok(queryService.getDetail(id));
    }

    /** 审核通过：CALCULATED → APPROVED。 */
    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAuthority('settlement:settle:approve')")
    @Audited(action = "SETTLEMENT_APPROVE", resourceType = "SETTLEMENT", resourceId = "#id")
    public ApiResponse<Void> approve(@PathVariable Long id) {
        executeService.approve(id, CurrentUserHolder.current().username());
        return ApiResponse.ok();
    }

    /** 执行打款：动账入商户账户；失败标 FAILED 并返回错误，FAILED 单可重试。 */
    @PostMapping("/{id}/execute")
    @PreAuthorize("hasAuthority('settlement:settle:execute')")
    public ApiResponse<Void> execute(@PathVariable Long id) {
        executeService.execute(id);
        return ApiResponse.ok();
    }
}
