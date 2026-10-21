package org.example.finflowbackend.risk.evaluate;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.example.finflowbackend.risk.RiskErrorCode;
import org.example.finflowbackend.risk.RiskRuleType;
import org.example.finflowbackend.risk.entity.RiskBlacklist;
import org.example.finflowbackend.risk.entity.RiskRule;
import org.example.finflowbackend.risk.mapper.RiskBlacklistMapper;
import org.example.finflowbackend.common.exception.BusinessException;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.util.Locale;
import java.util.Set;

/**
 * 黑名单：params {dimension: USER/IP/DEVICE}，名单命中直接拒绝（引擎短路，不累分）。
 * USER 维度按商户号匹配；要素缺失（未传 IP/设备号）视为未命中。
 */
@Component
public class BlacklistEvaluator implements RuleEvaluator {

    private static final Set<String> DIMENSIONS = Set.of(
            RiskBlacklist.DIMENSION_USER, RiskBlacklist.DIMENSION_IP, RiskBlacklist.DIMENSION_DEVICE);

    private final RiskBlacklistMapper blacklistMapper;

    public BlacklistEvaluator(RiskBlacklistMapper blacklistMapper) {
        this.blacklistMapper = blacklistMapper;
    }

    @Override
    public RiskRuleType type() {
        return RiskRuleType.BLACKLIST;
    }

    @Override
    public void validateParams(JsonNode params) {
        String dimension = params.get("dimension") == null ? null : params.get("dimension").asString();
        if (dimension == null || !DIMENSIONS.contains(dimension.toUpperCase(Locale.ROOT))) {
            throw new BusinessException(RiskErrorCode.RULE_PARAMS_INVALID, "dimension 必须为 USER/IP/DEVICE");
        }
    }

    @Override
    public RuleHit evaluate(RiskRule rule, JsonNode params, RiskFact fact) {
        String dimension = params.get("dimension").asString().toUpperCase(Locale.ROOT);
        String value = switch (dimension) {
            case RiskBlacklist.DIMENSION_USER -> String.valueOf(fact.merchantId());
            case RiskBlacklist.DIMENSION_IP -> fact.clientIp();
            case RiskBlacklist.DIMENSION_DEVICE -> fact.deviceId();
            default -> null;
        };
        if (value == null || value.isBlank()) {
            return RuleHit.miss();
        }
        Long count = blacklistMapper.selectCount(new LambdaQueryWrapper<RiskBlacklist>()
                .eq(RiskBlacklist::getDimension, dimension)
                .eq(RiskBlacklist::getValue, value));
        if (count != null && count > 0) {
            return RuleHit.hit(dimension + " " + value + " 命中黑名单");
        }
        return RuleHit.miss();
    }
}
