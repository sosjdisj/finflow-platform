package org.example.finflowbackend.system.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import org.example.finflowbackend.common.api.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

/**
 * 健康检查接口：验证工程可启动、Web 链路与统一响应可用。
 */
@RestController
@Tag(name = "健康检查")
@RequestMapping("/api/v1/health")
public class HealthController {

    @GetMapping
    public ApiResponse<HealthStatusVO> health() {
        return ApiResponse.ok(new HealthStatusVO("UP", LocalDateTime.now()));
    }
}
