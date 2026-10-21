package org.example.finflowbackend.audit.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import org.example.finflowbackend.audit.dto.AuditLogResponse;
import org.example.finflowbackend.audit.entity.AuditLog;
import org.example.finflowbackend.audit.service.AuditQueryService;
import org.example.finflowbackend.common.api.ApiResponse;
import org.example.finflowbackend.common.api.PageResponse;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;

/**
 * 审计日志接口（S35）：只提供查询与 CSV 导出，禁止删除/修改（审计只增不改）。
 */
@RestController
@Tag(name = "审计")
@RequestMapping("/api/v1/audit")
public class AuditController {

    private static final int EXPORT_LIMIT = 10000;
    private static final DateTimeFormatter CSV_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String[] CSV_HEADERS =
            {"ID", "操作人", "操作", "资源类型", "资源ID", "变更前快照", "变更后快照", "结果", "错误信息", "客户端IP", "操作时间"};

    private final AuditQueryService auditQueryService;

    public AuditController(AuditQueryService auditQueryService) {
        this.auditQueryService = auditQueryService;
    }

    @GetMapping("/logs")
    @PreAuthorize("hasAuthority('audit:log:list')")
    public ApiResponse<PageResponse<AuditLogResponse>> page(@RequestParam(defaultValue = "1") long page,
                                                            @RequestParam(defaultValue = "20") long size,
                                                            @RequestParam(required = false) String actorUsername,
                                                            @RequestParam(required = false) String action,
                                                            @RequestParam(required = false) String resourceType,
                                                            @RequestParam(required = false) String result,
                                                            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                                                            @RequestParam(required = false) LocalDateTime startTime,
                                                            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                                                            @RequestParam(required = false) LocalDateTime endTime) {
        return ApiResponse.ok(auditQueryService.page(page, size, actorUsername, action,
                resourceType, result, startTime, endTime));
    }

    /** CSV 导出：UTF-8 带 BOM（防 Excel 乱码）、中文表头、上限 10000 行。 */
    @GetMapping("/logs/export")
    @PreAuthorize("hasAuthority('audit:log:export')")
    public void export(@RequestParam(required = false) String actorUsername,
                       @RequestParam(required = false) String action,
                       @RequestParam(required = false) String resourceType,
                       @RequestParam(required = false) String result,
                       @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                       @RequestParam(required = false) LocalDateTime startTime,
                       @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                       @RequestParam(required = false) LocalDateTime endTime,
                       HttpServletResponse response) throws IOException {
        List<AuditLog> logs = auditQueryService.listForExport(actorUsername, action, resourceType,
                result, startTime, endTime, EXPORT_LIMIT);

        String fileName = "audit-" + DateTimeFormatter.ofPattern("yyyyMMddHHmmss")
                .format(LocalDateTime.now()) + ".csv";
        response.setContentType("text/csv;charset=UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=" + fileName);

        PrintWriter writer = response.getWriter();
        writer.write('\uFEFF');
        writer.println(Arrays.stream(CSV_HEADERS).map(AuditController::csvField)
                .reduce((a, b) -> a + "," + b).orElse(""));
        for (AuditLog log : logs) {
            writer.println(toCsvLine(log));
        }
        writer.flush();
    }

    private static String toCsvLine(AuditLog log) {
        return String.join(",",
                csvField(log.getId() == null ? "" : String.valueOf(log.getId())),
                csvField(log.getActorUsername()),
                csvField(log.getAction()),
                csvField(log.getResourceType()),
                csvField(log.getResourceId()),
                csvField(log.getBeforeSnapshot()),
                csvField(log.getAfterSnapshot()),
                csvField(log.getResult()),
                csvField(log.getErrorMessage()),
                csvField(log.getClientIp()),
                csvField(log.getCreatedAt() == null ? "" : CSV_TIME.format(log.getCreatedAt())));
    }

    /** CSV 字段统一加引号，内部引号翻倍，换行替换为空格（快照 JSON 含逗号/引号）。 */
    private static String csvField(String value) {
        if (value == null) {
            return "\"\"";
        }
        String safe = value.replace("\r", " ").replace("\n", " ");
        return "\"" + safe.replace("\"", "\"\"") + "\"";
    }
}
