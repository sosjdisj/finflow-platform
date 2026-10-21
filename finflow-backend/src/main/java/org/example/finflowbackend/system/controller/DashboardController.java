package org.example.finflowbackend.system.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import org.example.finflowbackend.common.api.ApiResponse;
import org.example.finflowbackend.system.dto.DashboardChannelItem;
import org.example.finflowbackend.system.dto.DashboardMerchantItem;
import org.example.finflowbackend.system.dto.DashboardSummaryResponse;
import org.example.finflowbackend.system.dto.DashboardTrendsResponse;
import org.example.finflowbackend.system.service.DashboardStatsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Dashboard 统计接口（S36）：首页登录即可见，指标为平台内部只读聚合，不加操作权限点。
 */
@RestController
@Tag(name = "Dashboard")
@RequestMapping("/api/v1/dashboard")
public class DashboardController {

    private final DashboardStatsService statsService;

    public DashboardController(DashboardStatsService statsService) {
        this.statsService = statsService;
    }

    @GetMapping("/summary")
    public ApiResponse<DashboardSummaryResponse> summary() {
        return ApiResponse.ok(statsService.summary());
    }

    @GetMapping("/trends")
    public ApiResponse<DashboardTrendsResponse> trends(@RequestParam(required = false) Integer days) {
        return ApiResponse.ok(statsService.trends(days));
    }

    @GetMapping("/channel-share")
    public ApiResponse<List<DashboardChannelItem>> channelShare(@RequestParam(required = false) Integer days) {
        return ApiResponse.ok(statsService.channelShare(days));
    }

    @GetMapping("/merchant-top")
    public ApiResponse<List<DashboardMerchantItem>> merchantTop(@RequestParam(required = false) Integer days) {
        return ApiResponse.ok(statsService.merchantTop(days));
    }
}
