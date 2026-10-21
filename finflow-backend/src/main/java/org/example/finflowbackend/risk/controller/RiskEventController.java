package org.example.finflowbackend.risk.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import org.example.finflowbackend.common.api.ApiResponse;
import org.example.finflowbackend.common.api.PageResponse;
import org.example.finflowbackend.risk.entity.RiskEvent;
import org.example.finflowbackend.risk.service.RiskEventService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

/**
 * 风险事件查询接口（只增不改的检查留痕）。权限编码与 V27 初始化数据一一对应。
 */
@RestController
@Tag(name = "风险事件")
@RequestMapping("/api/v1/risk/events")
public class RiskEventController {

    private final RiskEventService eventService;

    public RiskEventController(RiskEventService eventService) {
        this.eventService = eventService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('risk:event:list')")
    public ApiResponse<PageResponse<RiskEvent>> page(@RequestParam(defaultValue = "1") long page,
                                                     @RequestParam(defaultValue = "20") long size,
                                                     @RequestParam(required = false) Long orderId,
                                                     @RequestParam(required = false) Long merchantId,
                                                     @RequestParam(required = false) String verdict,
                                                     @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                                                     @RequestParam(required = false) LocalDateTime createdFrom,
                                                     @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                                                     @RequestParam(required = false) LocalDateTime createdTo) {
        return ApiResponse.ok(eventService.page(page, size, orderId, merchantId, verdict, createdFrom, createdTo));
    }
}
