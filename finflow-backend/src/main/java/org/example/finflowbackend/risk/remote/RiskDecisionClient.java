package org.example.finflowbackend.risk.remote;

import org.example.finflowbackend.risk.api.RiskDecisionApi;
import org.example.finflowbackend.risk.api.RiskDecisionRequest;
import org.example.finflowbackend.risk.api.RiskDecisionResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * 远程风控决策客户端（M11）：由 {@link RiskRemoteConfig} 在 {@code finflow.risk.mode=remote} 时注册。
 * 服务名为风控服务在 Nacos 的注册名；请求 / 响应沿用 M09 的 risk.api 契约，不新增字段。
 * Sentinel 包裹后由 {@link RiskDecisionFallbackFactory} 按 M09 超时策略降级。
 */
@FeignClient(name = "finflow-risk", contextId = "riskDecision",
        path = "/api/v1/internal/risk", fallbackFactory = RiskDecisionFallbackFactory.class)
public interface RiskDecisionClient extends RiskDecisionApi {

    @Override
    @PostMapping("/decisions")
    RiskDecisionResponse decide(@RequestBody RiskDecisionRequest request);
}
