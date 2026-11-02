package org.example.finflowbackend.risk.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.example.finflowbackend.audit.api.AuditApi;
import org.example.finflowbackend.common.api.PageResponse;
import org.example.finflowbackend.common.exception.BusinessException;
import org.example.finflowbackend.risk.RiskErrorCode;
import org.example.finflowbackend.risk.RiskRuleType;
import org.example.finflowbackend.risk.entity.RiskRule;
import org.example.finflowbackend.risk.entity.RiskRuleVersion;
import org.example.finflowbackend.risk.evaluate.RuleEvaluator;
import org.example.finflowbackend.risk.mapper.RiskRuleMapper;
import org.example.finflowbackend.risk.mapper.RiskRuleVersionMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;
import java.util.stream.Collectors;

/**
 * 风控规则管理（S30）：动态增删改 + 启停。
 * 参数合法性在写入时按类型交给对应求值器校验，引擎求值时即可信任库内配置。
 * M10：每次变更在同一事务内递增 risk_rule.version 并写 risk_rule_version 完整快照（只增不改）。
 */
@Service
public class RiskRuleService {

    private final RiskRuleMapper ruleMapper;
    private final RiskRuleVersionMapper versionMapper;
    private final Map<RiskRuleType, RuleEvaluator> evaluators;
    private final ObjectMapper objectMapper;
    private final AuditApi auditApi;

    public RiskRuleService(RiskRuleMapper ruleMapper, RiskRuleVersionMapper versionMapper,
                           java.util.List<RuleEvaluator> evaluatorList,
                           ObjectMapper objectMapper, AuditApi auditApi) {
        this.ruleMapper = ruleMapper;
        this.versionMapper = versionMapper;
        this.evaluators = evaluatorList.stream()
                .collect(Collectors.toUnmodifiableMap(RuleEvaluator::type, e -> e));
        this.objectMapper = objectMapper;
        this.auditApi = auditApi;
    }

    @Transactional
    public RiskRule create(RiskRule rule) {
        validate(rule);
        rule.setId(null);
        rule.setVersion(1);
        try {
            ruleMapper.insert(rule);
        } catch (DuplicateKeyException e) {
            throw new BusinessException(RiskErrorCode.RULE_NAME_EXISTS);
        }
        recordVersion(rule, RiskRuleVersion.CHANGE_CREATE);
        return rule;
    }

    @Transactional
    public RiskRule update(Long id, RiskRule incoming) {
        RiskRule rule = requireRule(id);
        RiskRule before = snapshot(rule);
        rule.setRuleName(incoming.getRuleName());
        rule.setRuleType(incoming.getRuleType());
        rule.setParams(incoming.getParams());
        rule.setScore(incoming.getScore());
        rule.setPriority(incoming.getPriority());
        rule.setEnabled(incoming.getEnabled());
        rule.setRemark(incoming.getRemark());
        rule.setVersion(nextVersion(rule.getVersion()));
        validate(rule);
        try {
            ruleMapper.updateById(rule);
        } catch (DuplicateKeyException e) {
            throw new BusinessException(RiskErrorCode.RULE_NAME_EXISTS);
        }
        recordVersion(rule, RiskRuleVersion.CHANGE_UPDATE);
        auditApi.submit(before, rule);
        return rule;
    }

    /** 启停开关（S31 管理页的开关控件）：下一笔交易即时生效（求值每次实时读规则）。 */
    @Transactional
    public RiskRule toggle(Long id, boolean enabled) {
        RiskRule rule = requireRule(id);
        RiskRule before = snapshot(rule);
        rule.setEnabled(enabled);
        rule.setVersion(nextVersion(rule.getVersion()));
        ruleMapper.updateById(rule);
        recordVersion(rule, RiskRuleVersion.CHANGE_TOGGLE);
        auditApi.submit(before, rule);
        return rule;
    }

    @Transactional
    public void delete(Long id) {
        RiskRule rule = requireRule(id);
        // 删除也是一次变更：先留一行 DELETE 快照（版本号再 +1），再删活动规则；历史永久保留
        rule.setVersion(nextVersion(rule.getVersion()));
        recordVersion(rule, RiskRuleVersion.CHANGE_DELETE);
        ruleMapper.deleteById(id);
    }

    @Transactional(readOnly = true)
    public PageResponse<RiskRule> page(long page, long size, String ruleName, String ruleType,
                                       Boolean enabled) {
        Page<RiskRule> result = ruleMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<RiskRule>()
                        .like(ruleName != null && !ruleName.isBlank(), RiskRule::getRuleName, ruleName)
                        .eq(ruleType != null && !ruleType.isBlank(), RiskRule::getRuleType, ruleType)
                        .eq(enabled != null, RiskRule::getEnabled, enabled)
                        .orderByAsc(RiskRule::getPriority)
                        .orderByAsc(RiskRule::getId));
        return PageResponse.of(result);
    }

    @Transactional(readOnly = true)
    public RiskRule get(Long id) {
        return requireRule(id);
    }

    /** 写入前校验：类型存在、分值/优先级非负、参数 JSON 能解析且过对应求值器校验。 */
    private void validate(RiskRule rule) {
        RiskRuleType type = RiskRuleType.parse(rule.getRuleType());
        if (type == null) {
            throw new BusinessException(RiskErrorCode.RULE_TYPE_INVALID,
                    "规则类型仅支持 AMOUNT_LIMIT / FREQUENCY / BLACKLIST / SAME_IP_MULTI_ACCOUNT");
        }
        if (rule.getScore() == null || rule.getScore() < 0 || rule.getPriority() == null || rule.getPriority() < 0) {
            throw new BusinessException(RiskErrorCode.RULE_PARAMS_INVALID, "分值与优先级必须为非负整数");
        }
        if (rule.getEnabled() == null) {
            rule.setEnabled(true);
        }
        JsonNode params;
        try {
            params = objectMapper.readTree(rule.getParams() == null ? "" : rule.getParams());
        } catch (RuntimeException e) {
            throw new BusinessException(RiskErrorCode.RULE_PARAMS_INVALID, "参数不是合法 JSON");
        }
        evaluators.get(type).validateParams(params);
    }

    private RiskRule requireRule(Long id) {
        RiskRule rule = ruleMapper.selectById(id);
        if (rule == null) {
            throw new BusinessException(RiskErrorCode.RULE_NOT_FOUND);
        }
        return rule;
    }

    private static int nextVersion(Integer current) {
        return current == null ? 1 : current + 1;
    }

    /** 写一行完整快照（与规则变更同一事务）；created_at 由库默认值填充。 */
    private void recordVersion(RiskRule rule, String changeType) {
        RiskRuleVersion version = new RiskRuleVersion();
        version.setRuleId(rule.getId());
        version.setVersion(rule.getVersion());
        version.setRuleName(rule.getRuleName());
        version.setRuleType(rule.getRuleType());
        version.setParams(rule.getParams());
        version.setScore(rule.getScore());
        version.setPriority(rule.getPriority());
        version.setEnabled(rule.getEnabled());
        version.setRemark(rule.getRemark());
        version.setChangeType(changeType);
        versionMapper.insert(version);
    }

    /** 审计改前快照：浅拷贝一份，避免原实体原地修改污染 before 值。 */
    private static RiskRule snapshot(RiskRule rule) {
        RiskRule copy = new RiskRule();
        copy.setId(rule.getId());
        copy.setRuleName(rule.getRuleName());
        copy.setRuleType(rule.getRuleType());
        copy.setParams(rule.getParams());
        copy.setScore(rule.getScore());
        copy.setPriority(rule.getPriority());
        copy.setEnabled(rule.getEnabled());
        copy.setRemark(rule.getRemark());
        copy.setVersion(rule.getVersion());
        copy.setCreatedAt(rule.getCreatedAt());
        copy.setUpdatedAt(rule.getUpdatedAt());
        return copy;
    }
}
