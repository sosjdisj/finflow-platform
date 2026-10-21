package org.example.finflowbackend.risk.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.example.finflowbackend.common.event.DomainEvent;
import org.example.finflowbackend.common.event.DomainEventPublisher;
import org.example.finflowbackend.common.exception.BusinessException;
import org.example.finflowbackend.payment.risk.RiskCheckContext;
import org.example.finflowbackend.payment.risk.RiskChecker;
import org.example.finflowbackend.risk.RiskErrorCode;
import org.example.finflowbackend.risk.RiskRuleType;
import org.example.finflowbackend.system.config.ConfigKeys;
import org.example.finflowbackend.system.service.ConfigService;
import org.example.finflowbackend.risk.entity.RiskEvent;
import org.example.finflowbackend.risk.entity.RiskRule;
import org.example.finflowbackend.risk.event.RiskInterceptedPayload;
import org.example.finflowbackend.risk.event.RiskTopics;
import org.example.finflowbackend.risk.evaluate.RuleEvaluator;
import org.example.finflowbackend.risk.evaluate.RuleHit;
import org.example.finflowbackend.risk.evaluate.RiskFact;
import org.example.finflowbackend.risk.mapper.RiskEventMapper;
import org.example.finflowbackend.risk.mapper.RiskRuleMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
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
 * 风控规则求值引擎（S30）：实现 payment 模块的 {@link RiskChecker} 接口，替换 S15 的全放行默认实现
 * （依赖倒置：支付编排只依赖抽象，感知不到规则引擎的存在）。
 * 求值：加载启用规则按优先级执行 → 黑名单命中短路直接拒绝 → 其余命中累加风险分 →
 * 对照三档阈值（读配置中心 risk.review/reject-threshold，S37）判 ALLOW / REVIEW / REJECT，每次检查写 risk_event 留痕；
 * 拦截（REVIEW/REJECT）时发布风控拦截事件，作为通知中心「风控拦截」模板的事件源（S33）。
 */
@Service
public class RiskEngine implements RiskChecker {

    private static final Logger log = LoggerFactory.getLogger(RiskEngine.class);
    private static final DateTimeFormatter NO_DATE = DateTimeFormatter.ofPattern("yyyyMMdd", Locale.ROOT);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final RiskRuleMapper ruleMapper;
    private final RiskEventMapper eventMapper;
    private final Map<RiskRuleType, RuleEvaluator> evaluators;
    private final ConfigService configService;
    private final ObjectMapper objectMapper;
    private final DomainEventPublisher eventPublisher;

    public RiskEngine(RiskRuleMapper ruleMapper, RiskEventMapper eventMapper,
                      List<RuleEvaluator> evaluatorList, ConfigService configService, ObjectMapper objectMapper,
                      DomainEventPublisher eventPublisher) {
        this.ruleMapper = ruleMapper;
        this.eventMapper = eventMapper;
        this.evaluators = evaluatorList.stream()
                .collect(java.util.stream.Collectors.toUnmodifiableMap(RuleEvaluator::type, e -> e));
        this.configService = configService;
        this.objectMapper = objectMapper;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public RiskCheckContext.Decision check(RiskCheckContext context) {
        RiskFact fact = new RiskFact(context.orderId(), context.merchantId(), context.amount(),
                context.clientIp(), context.deviceId());

        List<RiskRule> rules = ruleMapper.selectList(new LambdaQueryWrapper<RiskRule>()
                .eq(RiskRule::getEnabled, true)
                .orderByAsc(RiskRule::getPriority)
                .orderByAsc(RiskRule::getId));

        List<Map<String, Object>> hits = new ArrayList<>();
        int totalScore = 0;
        RiskCheckContext.Decision decision = null;
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
                        rule.getId(), rule.getRuleName(), context.orderId(), e);
                continue;
            }
            if (!hit.hit()) {
                continue;
            }
            totalScore += rule.getScore();
            hits.add(hitDetail(rule, hit));
            // 黑名单命中直接拒绝，短路不再执行后续规则
            if (RiskRuleType.BLACKLIST == RiskRuleType.parse(rule.getRuleType())) {
                decision = RiskCheckContext.Decision.reject(rule.getRuleName() + ": " + hit.detail());
                break;
            }
        }
        if (decision == null) {
            decision = verdict(totalScore);
        }
        recordEvent(context, hits, totalScore, decision);
        // 拦截（REVIEW/REJECT）发布事件供通知中心消费；放行不发（S33）
        if (decision.verdict() != RiskCheckContext.Verdict.ALLOW) {
            eventPublisher.publish(RiskTopics.TOPIC_INTERCEPTED, String.valueOf(context.orderId()),
                    DomainEvent.of(RiskTopics.TYPE_RISK_INTERCEPTED, new RiskInterceptedPayload(
                            context.orderId(), context.merchantId(), decision.verdict().name(),
                            totalScore, decision.reason())));
        }
        return decision;
    }

    /** 阈值三档判决（阈值读配置中心 S37）：总分 ≥ 拒绝阈值判 REJECT，否则 ≥ 审核阈值判 REVIEW，否则放行。 */
    private RiskCheckContext.Decision verdict(int totalScore) {
        long rejectThreshold = configService.getLong(ConfigKeys.RISK_REJECT_THRESHOLD);
        long reviewThreshold = configService.getLong(ConfigKeys.RISK_REVIEW_THRESHOLD);
        if (totalScore >= rejectThreshold) {
            return RiskCheckContext.Decision.reject("风险分 " + totalScore + " 达到拒绝阈值 " + rejectThreshold);
        }
        if (totalScore >= reviewThreshold) {
            return RiskCheckContext.Decision.review("风险分 " + totalScore + " 达到审核阈值 " + reviewThreshold);
        }
        return RiskCheckContext.Decision.allow();
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
    private void recordEvent(RiskCheckContext context, List<Map<String, Object>> hits,
                             int totalScore, RiskCheckContext.Decision decision) {
        try {
            RiskEvent event = new RiskEvent();
            event.setEventNo(nextEventNo());
            event.setOrderId(context.orderId());
            event.setMerchantId(context.merchantId());
            event.setAmount(context.amount());
            event.setClientIp(context.clientIp());
            event.setDeviceId(context.deviceId());
            event.setHitRules(hits.isEmpty() ? null : objectMapper.writeValueAsString(hits));
            event.setTotalScore(totalScore);
            event.setVerdict(decision.verdict().name());
            event.setCreatedAt(LocalDateTime.now());
            eventMapper.insert(event);
        } catch (Exception e) {
            log.warn("[风控] 风险事件留痕失败（不阻断交易）: orderId={}", context.orderId(), e);
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
