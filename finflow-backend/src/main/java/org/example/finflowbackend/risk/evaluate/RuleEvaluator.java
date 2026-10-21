package org.example.finflowbackend.risk.evaluate;

import org.example.finflowbackend.risk.RiskRuleType;
import org.example.finflowbackend.risk.entity.RiskRule;
import tools.jackson.databind.JsonNode;

/**
 * 规则求值器（策略接口）：每种规则类型一个实现，Spring 注入后由求值器引擎按类型分发。
 * 新增规则类型 = 新增枚举值 + 新增本接口实现，引擎与其余求值器零改动。
 */
public interface RuleEvaluator {

    /** 本求值器负责的规则类型。 */
    RiskRuleType type();

    /** 校验规则参数（规则增改时调用），不合法抛业务异常。 */
    void validateParams(JsonNode params);

    /** 求值：要素是否命中该规则。 */
    RuleHit evaluate(RiskRule rule, JsonNode params, RiskFact fact);
}
