package org.example.finflowbackend.common.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI 文档配置（S40）：全局声明 Bearer JWT 认证，
 * Swagger UI 右上角 Authorize 填登录返回的 accessToken 即可带凭证调试任意接口。
 */
@Configuration
public class OpenApiConfig {

    private static final String SCHEME_NAME = "bearerAuth";

    @Bean
    public OpenAPI finflowOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("FinFlow 支付平台 API")
                        .description("企业级支付与资金结算平台（模块化单体）。"
                                + "统一响应 code=0 表示成功；金额单位一律为分；响应携带 traceId 可与日志对账排查。")
                        .version("0.0.1"))
                .components(new Components().addSecuritySchemes(SCHEME_NAME,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(SCHEME_NAME));
    }
}
