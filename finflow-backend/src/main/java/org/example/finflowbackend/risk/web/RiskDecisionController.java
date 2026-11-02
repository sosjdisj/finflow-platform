package org.example.finflowbackend.risk.web;

import org.example.finflowbackend.risk.api.RiskDecisionApi;
import org.example.finflowbackend.risk.api.RiskDecisionRequest;
import org.example.finflowbackend.risk.api.RiskDecisionResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 风控服务内部决策端点（M11）：只在风控服务进程注册（{@code finflow.risk.provider=true}）。
 * backend 经 Feign 调用；不对公网 / 运营后台暴露（M13 网关会屏蔽 internal 路径）。
 * 请求 / 响应为 M09 的 risk.api 契约，规则求值与留痕仍由 RiskEngine 在风控进程内完成。
 */
@RestController
@RequestMapping("/api/v1/internal/risk")
@ConditionalOnProperty(name = "finflow.risk.provider", havingValue = "true")
public class RiskDecisionController {

    private final RiskDecisionApi decisionApi;

    public RiskDecisionController(RiskDecisionApi decisionApi) {
        this.decisionApi = decisionApi;
    }

    @PostMapping("/decisions")
    public RiskDecisionResponse decide(@RequestBody RiskDecisionRequest request) {
        return decisionApi.decide(request);
    }
}
