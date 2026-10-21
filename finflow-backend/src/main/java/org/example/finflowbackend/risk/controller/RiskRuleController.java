package org.example.finflowbackend.risk.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.example.finflowbackend.audit.annotation.Audited;
import org.example.finflowbackend.common.api.ApiResponse;
import org.example.finflowbackend.common.api.PageResponse;
import org.example.finflowbackend.risk.dto.RuleStatusRequest;
import org.example.finflowbackend.risk.dto.RuleUpsertRequest;
import org.example.finflowbackend.risk.entity.RiskRule;
import org.example.finflowbackend.risk.service.RiskRuleService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 风控规则管理接口。规则改动下一笔交易即时生效（求值每次实时读库）。
 * 权限编码与 V27 初始化数据一一对应。
 */
@RestController
@Tag(name = "风控规则")
@RequestMapping("/api/v1/risk/rules")
public class RiskRuleController {

    private final RiskRuleService ruleService;

    public RiskRuleController(RiskRuleService ruleService) {
        this.ruleService = ruleService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('risk:rule:list')")
    public ApiResponse<PageResponse<RiskRule>> page(@RequestParam(defaultValue = "1") long page,
                                                    @RequestParam(defaultValue = "20") long size,
                                                    @RequestParam(required = false) String ruleName,
                                                    @RequestParam(required = false) String ruleType,
                                                    @RequestParam(required = false) Boolean enabled) {
        return ApiResponse.ok(ruleService.page(page, size, ruleName, ruleType, enabled));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('risk:rule:list')")
    public ApiResponse<RiskRule> detail(@PathVariable Long id) {
        return ApiResponse.ok(ruleService.get(id));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('risk:rule:create')")
    public ApiResponse<RiskRule> create(@Valid @RequestBody RuleUpsertRequest request) {
        return ApiResponse.ok(ruleService.create(fromRequest(request)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('risk:rule:update')")
    @Audited(action = "RISK_RULE_UPDATE", resourceType = "RISK_RULE", resourceId = "#id")
    public ApiResponse<RiskRule> update(@PathVariable Long id,
                                        @Valid @RequestBody RuleUpsertRequest request) {
        return ApiResponse.ok(ruleService.update(id, fromRequest(request)));
    }

    /** 启停开关。 */
    @PutMapping("/{id}/status")
    @PreAuthorize("hasAuthority('risk:rule:update')")
    @Audited(action = "RISK_RULE_TOGGLE", resourceType = "RISK_RULE", resourceId = "#id")
    public ApiResponse<RiskRule> toggle(@PathVariable Long id, @Valid @RequestBody RuleStatusRequest request) {
        return ApiResponse.ok(ruleService.toggle(id, request.enabled()));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('risk:rule:delete')")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        ruleService.delete(id);
        return ApiResponse.ok(null);
    }

    private RiskRule fromRequest(RuleUpsertRequest request) {
        RiskRule rule = new RiskRule();
        rule.setRuleName(request.ruleName());
        rule.setRuleType(request.ruleType());
        rule.setParams(request.params());
        rule.setScore(request.score());
        rule.setPriority(request.priority());
        rule.setEnabled(request.enabled());
        rule.setRemark(request.remark());
        return rule;
    }
}
