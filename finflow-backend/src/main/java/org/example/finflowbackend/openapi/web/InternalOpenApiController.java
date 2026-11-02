package org.example.finflowbackend.openapi.web;

import org.example.finflowbackend.common.api.ApiResponse;
import org.example.finflowbackend.common.api.ErrorCode;
import org.example.finflowbackend.openapi.api.OpenApiErrorCode;
import org.example.finflowbackend.openapi.security.InternalAccessGuard;
import org.example.finflowbackend.openapi.service.OpenApiAccessProfileService;
import org.example.finflowbackend.openapi.web.dto.OpenApiAccessProfileResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 网关注入档案接口（M13）：只给 finflow-gateway 用，属于内网端点，不对外暴露。
 *
 * <p>路径沿用已有 /api/v1/internal 前缀（SecurityConfig 已放行后台 JWT），鉴权由
 * {@link InternalAccessGuard} 的共享令牌承担；凭证无效返回 404 + 47005，网关据此拒绝商户请求。</p>
 */
@RestController
@RequestMapping("/api/v1/internal/openapi")
public class InternalOpenApiController {

    private final OpenApiAccessProfileService profileService;
    private final InternalAccessGuard internalAccessGuard;

    public InternalOpenApiController(OpenApiAccessProfileService profileService,
                                     InternalAccessGuard internalAccessGuard) {
        this.profileService = profileService;
        this.internalAccessGuard = internalAccessGuard;
    }

    @GetMapping("/profiles/{apiKey}")
    public ResponseEntity<ApiResponse<OpenApiAccessProfileResponse>> profile(
            @PathVariable String apiKey,
            @RequestHeader(value = InternalAccessGuard.HEADER_TOKEN, required = false) String token) {
        if (!internalAccessGuard.matches(token)) {
            return ResponseEntity.status(ErrorCode.UNAUTHORIZED.getHttpStatus())
                    .body(ApiResponse.fail(ErrorCode.UNAUTHORIZED));
        }
        return profileService.profile(apiKey)
                .map(profile -> ResponseEntity.ok(ApiResponse.ok(profile)))
                .orElseGet(() -> ResponseEntity.status(OpenApiErrorCode.CREDENTIAL_INVALID.getHttpStatus())
                        .body(ApiResponse.fail(OpenApiErrorCode.CREDENTIAL_INVALID)));
    }
}
