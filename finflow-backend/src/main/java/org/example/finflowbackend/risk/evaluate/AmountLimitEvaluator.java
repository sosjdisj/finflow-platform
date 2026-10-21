package org.example.finflowbackend.risk.evaluate;

import org.example.finflowbackend.risk.RiskErrorCode;
import org.example.finflowbackend.risk.RiskRuleType;
import org.example.finflowbackend.risk.entity.RiskRule;
import org.example.finflowbackend.common.exception.BusinessException;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

/**
 * 单笔金额限额：params {amountLimit}（分），订单金额 > 限额命中。
 * 报告验收示例「金额 > 10000 元加 30 分」即本类型（amountLimit=1000000, score=30）。
 */
@Component
public class AmountLimitEvaluator implements RuleEvaluator {

    @Override
    public RiskRuleType type() {
        return RiskRuleType.AMOUNT_LIMIT;
    }

    @Override
    public void validateParams(JsonNode params) {
        if (Params.requireLong(params, "amountLimit") <= 0) {
            throw new BusinessException(RiskErrorCode.RULE_PARAMS_INVALID, "amountLimit 必须为正数（分）");
        }
    }

    @Override
    public RuleHit evaluate(RiskRule rule, JsonNode params, RiskFact fact) {
        long amountLimit = Params.requireLong(params, "amountLimit");
        if (fact.amount() > amountLimit) {
            return RuleHit.hit("单笔金额 " + fact.amount() + " 分超过限额 " + amountLimit + " 分");
        }
        return RuleHit.miss();
    }
}
