package org.example.finflowbackend.risk.evaluate;

import org.example.finflowbackend.risk.RiskErrorCode;
import org.example.finflowbackend.risk.RiskRuleType;
import org.example.finflowbackend.risk.entity.RiskRule;
import org.example.finflowbackend.common.exception.BusinessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.util.List;

/**
 * 同 IP 多账户：params {maxAccounts, windowSeconds}，Redis ZSET 滑动窗口（score=毫秒时间戳，member=商户号）。
 * Lua 原子完成「清理窗口外成员 → 记录当前商户 → TTL 兜底 → 统计」（单次往返，SADD+EXPIRE 拆开非原子的老问题一并解决）；
 * key 含规则 ID，避免同类型多条规则共用计数互相污染。
 * 当前订单要素缺 IP 或商户号为空视为未命中；窗口内商户数（含当前）> maxAccounts 命中。
 */
@Component
public class SameIpMultiAccountEvaluator implements RuleEvaluator {

    public static final String KEY_PREFIX = "risk:ipacc:";

    /** KEYS[1]=计数键；ARGV: [当前毫秒时间戳, 窗口秒数, 商户号]，返回窗口内商户数 */
    private static final DefaultRedisScript<Long> SLIDING_WINDOW = new DefaultRedisScript<>("""
            redis.call('ZREMRANGEBYSCORE', KEYS[1], '-inf', ARGV[1] - ARGV[2])
            redis.call('ZADD', KEYS[1], ARGV[1], ARGV[3])
            redis.call('EXPIRE', KEYS[1], ARGV[2])
            return redis.call('ZCARD', KEYS[1])
            """, Long.class);

    private final StringRedisTemplate redisTemplate;

    public SameIpMultiAccountEvaluator(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public RiskRuleType type() {
        return RiskRuleType.SAME_IP_MULTI_ACCOUNT;
    }

    @Override
    public void validateParams(JsonNode params) {
        long maxAccounts = Params.requireLong(params, "maxAccounts");
        long windowSeconds = Params.requireLong(params, "windowSeconds");
        if (maxAccounts <= 0 || windowSeconds <= 0) {
            throw new BusinessException(RiskErrorCode.RULE_PARAMS_INVALID, "maxAccounts / windowSeconds 必须为正数");
        }
    }

    @Override
    public RuleHit evaluate(RiskRule rule, JsonNode params, RiskFact fact) {
        long maxAccounts = Params.requireLong(params, "maxAccounts");
        long windowSeconds = Params.requireLong(params, "windowSeconds");
        if (fact.clientIp() == null || fact.clientIp().isBlank() || fact.merchantId() == null) {
            return RuleHit.miss();
        }
        String key = KEY_PREFIX + rule.getId() + ":" + fact.clientIp();
        Long merchantCount = redisTemplate.execute(SLIDING_WINDOW, List.of(key),
                String.valueOf(System.currentTimeMillis()), String.valueOf(windowSeconds),
                String.valueOf(fact.merchantId()));
        if (merchantCount != null && merchantCount > maxAccounts) {
            return RuleHit.hit("IP " + fact.clientIp() + " 窗口 " + windowSeconds
                    + "s 内出现 " + merchantCount + " 个账户，超过阈值 " + maxAccounts);
        }
        return RuleHit.miss();
    }
}
