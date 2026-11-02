package org.example.finflowbackend.risk.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.example.finflowbackend.common.event.DomainEventPublisher;
import org.example.finflowbackend.common.exception.BusinessException;
import org.example.finflowbackend.contract.DomainEvent;
import org.example.finflowbackend.contract.EventTopics;
import org.example.finflowbackend.contract.EventTypes;
import org.example.finflowbackend.contract.RiskInterceptedPayload;
import org.example.finflowbackend.risk.RiskErrorCode;
import org.example.finflowbackend.risk.RiskRuleType;
import org.example.finflowbackend.risk.api.RiskDecisionRequest;
import org.example.finflowbackend.risk.api.RiskDecisionResponse;
import org.example.finflowbackend.risk.api.RiskVerdict;
import org.example.finflowbackend.risk.entity.RiskEvent;
import org.example.finflowbackend.risk.entity.RiskRule;
import org.example.finflowbackend.risk.evaluate.RiskFact;
import org.example.finflowbackend.risk.evaluate.RuleEvaluator;
import org.example.finflowbackend.risk.evaluate.RuleHit;
import org.example.finflowbackend.risk.mapper.RiskEventMapper;
import org.example.finflowbackend.risk.mapper.RiskRuleMapper;
import org.example.finflowbackend.common.tunable.TunableConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 风控规则求值引擎（S30 / M09）：经 risk.api 对支付编排暴露一次决策求值，替换 S15 的全放行默认实现
 * （依赖倒置：支付编排只依赖 {@code RiskDecisionApi} 抽象，感知不到规则引擎的存在）。
 * 求值：加载启用规则按优先级执行 → 黑名单命中短路直接拒绝 → 其余命中累加风险分 →
 * 对照三档阈值（M14 起读 Nacos 配置中心的 finflow.risk.thresholds）判 ALLOW / REVIEW / REJECT，每次检查写 risk_event 留痕；
 * 拦截（REVIEW/REJECT）时发布风控拦截事件，作为通知中心「风控拦截」模板的事件源（S33）。
 * 限时与超时降级不在本类职责内，统一由 {@link RiskDecisionService} 处理。
 */
@Service
public class RiskEngine {

    private static final Logger log = LoggerFactory.getLogger(RiskEngine.class);
    private static final DateTimeFormatter NO_DATE = DateTimeFormatter.ofPattern("yyyyMMdd", Locale.ROOT);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final RiskRuleMapper ruleMapper;
    private final RiskEventMapper eventMapper;
    private final Map<RiskRuleType, RuleEvaluator> evaluators;
    private final TunableConfig tunables;
    private final ObjectMapper objectMapper;
    private final DomainEventPublisher eventPublisher;

    public RiskEngine(RiskRuleMapper ruleMapper, RiskEventMapper eventMapper,
                      List<RuleEvaluator> evaluatorList, TunableConfig tunables, ObjectMapper objectMapper,
                      DomainEventPublisher eventPublisher) {
        this.ruleMapper = ruleMapper;
        this.eventMapper = eventMapper;
        this.evaluators = evaluatorList.stream()
                .collect(java.util.stream.Collectors.toUnmodifiableMap(RuleEvaluator::type, e -> e));
        this.tunables = tunables;
        this.objectMapper = objectMapper;
        this.eventPublisher = eventPublisher;
    }

    /** 单次风控决策：规则求值 + 留痕 + 拦截事件发布，返回三态判决。 */
    public RiskDecisionResponse evaluate(RiskDecisionRequest request) {
        RiskFact fact = new RiskFact(request.orderId(), request.merchantId(), request.amount(),
                request.clientIp(), request.deviceId());

        List<RiskRule> rules = ruleMapper.selectList(new LambdaQueryWrapper<RiskRule>()
                .eq(RiskRule::getEnabled, true)
                .orderByAsc(RiskRule::getPriority)
                .orderByAsc(RiskRule::getId));

        List<Map<String, Object>> hits = new ArrayList<>();
        int totalScore = 0;
        RiskDecisionResponse decision = null;
        for (RiskRule rule : rules) {
            RuleEvaluator evaluator = evaluators.get(RiskRuleType.parse(rule.getRuleType()));
            if (evaluator == null) {
                continue;
            }
            RuleHit hit;
            try {
                hit = evaluator.evaluate(rule, objectMapper.readTree(rule.getParams()), fact);
            } catch (Exception e) {
                // Redis 等基础设施故障降级为未命中（fail-open）：风控异常不阻断交易，留日志人工跟进
                log.warn("[风控] 规则求值异常，降级为未命中: ruleId={}, ruleName={}, orderId={}",
                        rule.getId(), rule.getRuleName(), request.orderId(), e);
                continue;
            }
            if (!hit.hit()) {
                continue;
            }
            totalScore += rule.getScore();
            hits.add(hitDetail(rule, hit));
            // 黑名单命中直接拒绝，短路不再执行后续规则
            if (RiskRuleType.BLACKLIST == RiskRuleType.parse(rule.getRuleType())) {
                decision = RiskDecisionResponse.reject(rule.getRuleName() + ": " + hit.detail());
                break;
            }
        }
        if (decision == null) {
            decision = verdict(totalScore);
        }
        recordEvent(request, hits, totalScore, decision);
        // 拦截（REVIEW/REJECT）发布事件供通知中心消费；放行不发（S33）
        if (decision.verdict() != RiskVerdict.ALLOW) {
            eventPublisher.publish(EventTopics.RISK_INTERCEPTED, String.valueOf(request.orderId()),
                    DomainEvent.of(EventTypes.RISK_INTERCEPTED, new RiskInterceptedPayload(
                            request.orderId(), request.merchantId(), request.platformOrderNo(),
                            decision.verdict().name(), totalScore, decision.reason())));
        }
        return decision;
    }

    /** 阈值三档判决（M14 阈值来自 Nacos，每次读取当前值）：总分 ≥ 拒绝阈值判 REJECT，否则 ≥ 审核阈值判 REVIEW，否则放行。 */
    private RiskDecisionResponse verdict(int totalScore) {
        long rejectThreshold = tunables.riskRejectThreshold();
        long reviewThreshold = tunables.riskReviewThreshold();
        if (totalScore >= rejectThreshold) {
            return RiskDecisionResponse.reject("风险分 " + totalScore + " 达到拒绝阈值 " + rejectThreshold);
        }
        if (totalScore >= reviewThreshold) {
            return RiskDecisionResponse.review("风险分 " + totalScore + " 达到审核阈值 " + reviewThreshold);
        }
        return RiskDecisionResponse.allow();
    }

    /** 命中明细快照（写入 risk_event.hit_rules 的 JSON 数组元素）。 */
    private Map<String, Object> hitDetail(RiskRule rule, RuleHit hit) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("ruleId", rule.getId());
        detail.put("ruleName", rule.getRuleName());
        detail.put("ruleType", rule.getRuleType());
        detail.put("score", rule.getScore());
        detail.put("detail", hit.detail());
        return detail;
    }

    /** 检查留痕：事件号 RE+yyyyMMdd+6 位随机；留痕失败不阻断交易（记日志降级）。 */
    private void recordEvent(RiskDecisionRequest request, List<Map<String, Object>> hits,
                             int totalScore, RiskDecisionResponse decision) {
        try {
            RiskEvent event = new RiskEvent();
            event.setEventNo(nextEventNo());
            event.setOrderId(request.orderId());
            event.setMerchantId(request.merchantId());
            event.setAmount(request.amount());
            event.setClientIp(request.clientIp());
            event.setDeviceId(request.deviceId());
            event.setHitRules(hits.isEmpty() ? null : objectMapper.writeValueAsString(hits));
            event.setTotalScore(totalScore);
            event.setVerdict(decision.verdict().name());
            event.setCreatedAt(LocalDateTime.now());
            eventMapper.insert(event);
        } catch (Exception e) {
            log.warn("[风控] 风险事件留痕失败（不阻断交易）: orderId={}", request.orderId(), e);
        }
    }

    private String nextEventNo() {
        String datePart = LocalDate.now().format(NO_DATE);
        for (int i = 0; i < 3; i++) {
            String eventNo = "RE" + datePart + "%06d".formatted(RANDOM.nextInt(1_000_000));
            if (eventMapper.selectCount(new LambdaQueryWrapper<RiskEvent>()
                    .eq(RiskEvent::getEventNo, eventNo)) == 0) {
                return eventNo;
            }
        }
        throw new BusinessException(RiskErrorCode.RULE_NOT_FOUND, "风险事件号生成失败，请重试");
    }
}
