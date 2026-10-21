package org.example.finflowbackend.risk.evaluate;

import org.example.finflowbackend.risk.RiskErrorCode;
import org.example.finflowbackend.risk.RiskRuleType;
import org.example.finflowbackend.risk.entity.RiskRule;
import org.example.finflowbackend.common.exception.BusinessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.time.Duration;

/**
 * 时间窗口内支付频次：params {windowSeconds, maxCount}，Redis INCR 计数（首笔写入时设 TTL）。
 * 计数包含当前这笔；窗口内笔数 > maxCount 命中（即第 maxCount+1 笔起命中）。
 */
@Component
public class FrequencyEvaluator implements RuleEvaluator {

    static final String KEY_PREFIX = "risk:freq:merchant:";

    private final StringRedisTemplate redisTemplate;

    public FrequencyEvaluator(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public RiskRuleType type() {
        return RiskRuleType.FREQUENCY;
    }

    @Override
    public void validateParams(JsonNode params) {
        long windowSeconds = Params.requireLong(params, "windowSeconds");
        long maxCount = Params.requireLong(params, "maxCount");
        if (windowSeconds <= 0 || maxCount <= 0) {
            throw new BusinessException(RiskErrorCode.RULE_PARAMS_INVALID, "windowSeconds / maxCount 必须为正数");
        }
    }

    @Override
    public RuleHit evaluate(RiskRule rule, JsonNode params, RiskFact fact) {
        long windowSeconds = Params.requireLong(params, "windowSeconds");
        long maxCount = Params.requireLong(params, "maxCount");
        String key = KEY_PREFIX + fact.merchantId();
        Long count = redisTemplate.opsForValue().increment(key);
        if (count != null && count == 1) {
            redisTemplate.expire(key, Duration.ofSeconds(windowSeconds));
        }
        if (count != null && count > maxCount) {
            return RuleHit.hit("时间窗口 " + windowSeconds + "s 内第 " + count + " 笔支付，超过阈值 " + maxCount);
        }
        return RuleHit.miss();
    }
}
