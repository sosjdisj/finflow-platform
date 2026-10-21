package org.example.finflowbackend.reconciliation.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import org.example.finflowbackend.auth.web.CurrentUserHolder;
import org.example.finflowbackend.common.api.ApiResponse;
import org.example.finflowbackend.common.api.PageResponse;
import org.example.finflowbackend.reconciliation.dto.DifferenceConfirmRequest;
import org.example.finflowbackend.reconciliation.dto.DifferenceCreateRequest;
import org.example.finflowbackend.reconciliation.dto.DifferenceHandleRequest;
import org.example.finflowbackend.reconciliation.dto.DifferenceIgnoreRequest;
import org.example.finflowbackend.reconciliation.entity.ReconciliationDifference;
import org.example.finflowbackend.reconciliation.service.ReconciliationDifferenceService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 对账差异处理接口（S29 人工闭环）。
 * 流：由匹配记录发起 → 审核确认（选定调账/补单）→ 执行处理（成功即关闭）或忽略。
 * 权限编码与 V26 初始化数据一一对应。
 */
@RestController
@Tag(name = "对账差异")
@RequestMapping("/api/v1/reconciliation/differences")
public class ReconciliationDifferenceController {

    private final ReconciliationDifferenceService differenceService;

    public ReconciliationDifferenceController(ReconciliationDifferenceService differenceService) {
        this.differenceService = differenceService;
    }

    /** 由对账匹配记录发起差异单（仅非 MATCHED 记录）。 */
    @PostMapping
    @PreAuthorize("hasAuthority('reconciliation:diff:create')")
    public ApiResponse<ReconciliationDifference> create(@RequestBody DifferenceCreateRequest request) {
        String operator = CurrentUserHolder.current().username();
        return ApiResponse.ok(differenceService.create(request.recordId(), operator));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('reconciliation:diff:list')")
    public ApiResponse<PageResponse<ReconciliationDifference>> page(@RequestParam(defaultValue = "1") long page,
                                                                    @RequestParam(defaultValue = "20") long size,
                                                                    @RequestParam(required = false) String diffNo,
                                                                    @RequestParam(required = false) Long taskId,
                                                                    @RequestParam(required = false) String diffType,
                                                                    @RequestParam(required = false) String status,
                                                                    @RequestParam(required = false) String merchantOrderNo) {
        return ApiResponse.ok(differenceService.page(page, size, diffNo, taskId, diffType, status, merchantOrderNo));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('reconciliation:diff:query')")
    public ApiResponse<ReconciliationDifference> detail(@PathVariable Long id) {
        return ApiResponse.ok(differenceService.get(id));
    }

    /** 审核确认：选定处理方式（ADJUST 调账 / SUPPLEMENT 补单）并留说明。 */
    @PostMapping("/{id}/confirm")
    @PreAuthorize("hasAuthority('reconciliation:diff:handle')")
    public ApiResponse<ReconciliationDifference> confirm(@PathVariable Long id,
                                                         @RequestBody DifferenceConfirmRequest request) {
        String operator = CurrentUserHolder.current().username();
        return ApiResponse.ok(differenceService.confirm(id, request.handleType(), request.handleRemark(), operator));
    }

    /** 忽略（误报 / 暂不处理）。 */
    @PostMapping("/{id}/ignore")
    @PreAuthorize("hasAuthority('reconciliation:diff:handle')")
    public ApiResponse<ReconciliationDifference> ignore(@PathVariable Long id,
                                                        @RequestBody(required = false) DifferenceIgnoreRequest request) {
        String operator = CurrentUserHolder.current().username();
        return ApiResponse.ok(differenceService.ignore(id,
                request == null ? null : request.remark(), operator));
    }

    /** 执行处理：调账走 ledger 调账入口（带符号金额）或补单登记凭证，成功即关闭。 */
    @PostMapping("/{id}/handle")
    @PreAuthorize("hasAuthority('reconciliation:diff:handle')")
    public ApiResponse<ReconciliationDifference> handle(@PathVariable Long id,
                                                        @RequestBody DifferenceHandleRequest request) {
        String operator = CurrentUserHolder.current().username();
        return ApiResponse.ok(differenceService.handle(id, request.accountId(), request.amount(),
                request.handleResult(), operator));
    }
}
