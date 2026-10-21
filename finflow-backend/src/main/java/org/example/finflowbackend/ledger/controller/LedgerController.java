package org.example.finflowbackend.ledger.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.example.finflowbackend.audit.annotation.Audited;
import org.example.finflowbackend.common.api.ApiResponse;
import org.example.finflowbackend.common.api.PageResponse;
import org.example.finflowbackend.ledger.dto.LedgerAdjustRequest;
import org.example.finflowbackend.ledger.dto.LedgerConsistencyResponse;
import org.example.finflowbackend.ledger.dto.LedgerRecordResponse;
import org.example.finflowbackend.ledger.service.LedgerService;
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
 * 资金流水接口。权限编码与 sys_permission 初始化数据一一对应（V16/V17）。
 */
@RestController
@Tag(name = "资金流水")
@RequestMapping("/api/v1/ledger")
public class LedgerController {

    private final LedgerService ledgerService;

    public LedgerController(LedgerService ledgerService) {
        this.ledgerService = ledgerService;
    }

    /** 流水分页：按账户 / 业务类型 / 业务单号 / 时间筛选。 */
    @GetMapping("/records")
    @PreAuthorize("hasAuthority('ledger:record:list')")
    public ApiResponse<PageResponse<LedgerRecordResponse>> page(@RequestParam(defaultValue = "1") long page,
                                                                @RequestParam(defaultValue = "20") long size,
                                                                @RequestParam(required = false) Long accountId,
                                                                @RequestParam(required = false) String bizType,
                                                                @RequestParam(required = false) String bizNo,
                                                                @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                                                                @RequestParam(required = false) LocalDateTime createdFrom,
                                                                @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                                                                @RequestParam(required = false) LocalDateTime createdTo) {
        return ApiResponse.ok(ledgerService.page(page, size, accountId, bizType, bizNo, createdFrom, createdTo));
    }

    /** 人工调账（仅超管）：调账必须走流水，审计埋点留 S35。 */
    @PostMapping("/adjustments")
    @PreAuthorize("hasAuthority('ledger:adjust')")
    @Audited(action = "LEDGER_ADJUST", resourceType = "ACCOUNT", resourceId = "#request.accountId")
    public ApiResponse<LedgerRecordResponse> adjust(@Valid @RequestBody LedgerAdjustRequest request) {
        return ApiResponse.ok(LedgerRecordResponse.from(
                ledgerService.adjust(request.accountId(), request.amount(), request.remark())));
    }

    /** 一致性校验：任意账户当前可用余额 = 初始 + 流水累加。 */
    @GetMapping("/accounts/{accountId}/consistency")
    @PreAuthorize("hasAuthority('ledger:consistency:query')")
    public ApiResponse<LedgerConsistencyResponse> consistency(@PathVariable Long accountId) {
        return ApiResponse.ok(ledgerService.consistencyCheck(accountId));
    }
}
