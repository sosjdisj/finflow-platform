package org.example.finflowbackend.reconciliation.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import org.example.finflowbackend.auth.web.CurrentUserHolder;
import org.example.finflowbackend.common.api.ApiResponse;
import org.example.finflowbackend.common.api.PageResponse;
import org.example.finflowbackend.reconciliation.dto.MatchSummaryResponse;
import org.example.finflowbackend.reconciliation.entity.ReconciliationBillItem;
import org.example.finflowbackend.reconciliation.entity.ReconciliationRecord;
import org.example.finflowbackend.reconciliation.entity.ReconciliationTask;
import org.example.finflowbackend.reconciliation.service.ReconciliationMatchService;
import org.example.finflowbackend.reconciliation.service.ReconciliationQueryService;
import org.example.finflowbackend.reconciliation.service.ReconciliationTaskService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;

/**
 * 对账任务接口（S27 输入侧：上传/查询；匹配在 S28）。权限编码与 V24 初始化数据一一对应。
 */
@RestController
@Tag(name = "对账任务")
@RequestMapping("/api/v1/reconciliation/tasks")
public class ReconciliationTaskController {

    private final ReconciliationTaskService taskService;
    private final ReconciliationQueryService queryService;
    private final ReconciliationMatchService matchService;

    public ReconciliationTaskController(ReconciliationTaskService taskService,
                                        ReconciliationQueryService queryService,
                                        ReconciliationMatchService matchService) {
        this.taskService = taskService;
        this.queryService = queryService;
        this.matchService = matchService;
    }

    /** 上传渠道账单 CSV 并同步解析，返回带解析统计的任务。 */
    @PostMapping
    @PreAuthorize("hasAuthority('reconciliation:task:upload')")
    public ApiResponse<ReconciliationTask> upload(@RequestParam("file") MultipartFile file,
                                                  @RequestParam String channelCode,
                                                  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                                                  @RequestParam LocalDate billDate) {
        var user = CurrentUserHolder.current();
        return ApiResponse.ok(taskService.upload(channelCode, billDate, file, user.username(), user.userId()));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('reconciliation:task:list')")
    public ApiResponse<PageResponse<ReconciliationTask>> page(@RequestParam(defaultValue = "1") long page,
                                                              @RequestParam(defaultValue = "20") long size,
                                                              @RequestParam(required = false) String taskNo,
                                                              @RequestParam(required = false) String channelCode,
                                                              @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                                                              @RequestParam(required = false) LocalDate billDate,
                                                              @RequestParam(required = false) String status) {
        return ApiResponse.ok(queryService.page(page, size, taskNo, channelCode, billDate, status));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('reconciliation:task:query')")
    public ApiResponse<ReconciliationTask> detail(@PathVariable Long id) {
        return ApiResponse.ok(queryService.getTask(id));
    }

    /** 暂存行明细分页（解析结果）。 */
    @GetMapping("/{id}/items")
    @PreAuthorize("hasAuthority('reconciliation:task:query')")
    public ApiResponse<PageResponse<ReconciliationBillItem>> items(@PathVariable Long id,
                                                                   @RequestParam(defaultValue = "1") long page,
                                                                   @RequestParam(defaultValue = "20") long size) {
        return ApiResponse.ok(queryService.pageItems(id, page, size));
    }

    /** 执行对账匹配（可重跑覆盖），返回五类结果汇总。 */
    @PostMapping("/{id}/match")
    @PreAuthorize("hasAuthority('reconciliation:task:match')")
    public ApiResponse<MatchSummaryResponse> match(@PathVariable Long id) {
        return ApiResponse.ok(matchService.match(id));
    }

    /** 匹配结果分页（对账结果页数据源），可按五类结果筛选。 */
    @GetMapping("/{id}/records")
    @PreAuthorize("hasAuthority('reconciliation:task:query')")
    public ApiResponse<PageResponse<ReconciliationRecord>> records(@PathVariable Long id,
                                                                   @RequestParam(defaultValue = "1") long page,
                                                                   @RequestParam(defaultValue = "20") long size,
                                                                   @RequestParam(required = false) String result) {
        return ApiResponse.ok(queryService.pageRecords(id, page, size, result));
    }
}
