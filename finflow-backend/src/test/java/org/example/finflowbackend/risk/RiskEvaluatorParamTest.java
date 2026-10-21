package org.example.finflowbackend.risk;

import org.example.finflowbackend.common.exception.BusinessException;
import org.example.finflowbackend.risk.evaluate.AmountLimitEvaluator;
import org.example.finflowbackend.risk.evaluate.BlacklistEvaluator;
import org.example.finflowbackend.risk.evaluate.FrequencyEvaluator;
import org.example.finflowbackend.risk.evaluate.RuleEvaluator;
import org.example.finflowbackend.risk.evaluate.SameIpMultiAccountEvaluator;
import org.example.finflowbackend.risk.mapper.RiskBlacklistMapper;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

/**
 * 规则参数校验单测：各求值器对「缺参 / 非法值」抛 RULE_PARAMS_INVALID，合法参数放行。
 * 写入时校验保证引擎求值时库内配置可信任。
 */
class RiskEvaluatorParamTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private void assertValid(RuleEvaluator evaluator, String params) {
        JsonNode node = objectMapper.readTree(params);
        assertDoesNotThrow(() -> evaluator.validateParams(node));
    }

    private void assertInvalid(RuleEvaluator evaluator, String params) {
        JsonNode node = objectMapper.readTree(params);
        BusinessException error = assertThrows(BusinessException.class, () -> evaluator.validateParams(node));
        assertEqualsParamsInvalid(error);
    }

    private void assertEqualsParamsInvalid(BusinessException error) {
        org.junit.jupiter.api.Assertions.assertEquals(RiskErrorCode.RULE_PARAMS_INVALID, error.getErrorCode());
    }

    @Test
    void amountLimitRequiresPositiveAmountLimit() {
        AmountLimitEvaluator evaluator = new AmountLimitEvaluator();
        assertValid(evaluator, """
                {"amountLimit": 1000000}""");
        assertInvalid(evaluator, "{}");
        assertInvalid(evaluator, """
                {"amountLimit": 0}""");
        assertInvalid(evaluator, """
                {"amountLimit": -1}""");
    }

    @Test
    void frequencyRequiresPositiveWindowAndCount() {
        FrequencyEvaluator evaluator = new FrequencyEvaluator(mock(StringRedisTemplate.class));
        assertValid(evaluator, """
                {"windowSeconds": 60, "maxCount": 5}""");
        assertInvalid(evaluator, """
                {"windowSeconds": 60}""");
        assertInvalid(evaluator, """
                {"windowSeconds": 0, "maxCount": 5}""");
    }

    @Test
    void blacklistRequiresKnownDimension() {
        BlacklistEvaluator evaluator = new BlacklistEvaluator(mock(RiskBlacklistMapper.class));
        assertValid(evaluator, """
                {"dimension": "USER"}""");
        assertValid(evaluator, """
                {"dimension": "ip"}""");
        assertInvalid(evaluator, "{}");
        assertInvalid(evaluator, """
                {"dimension": "FOO"}""");
    }

    @Test
    void sameIpMultiAccountRequiresPositiveParams() {
        SameIpMultiAccountEvaluator evaluator = new SameIpMultiAccountEvaluator(mock(StringRedisTemplate.class));
        assertValid(evaluator, """
                {"maxAccounts": 3, "windowSeconds": 3600}""");
        assertInvalid(evaluator, """
                {"maxAccounts": 3}""");
        assertInvalid(evaluator, """
                {"maxAccounts": 0, "windowSeconds": 60}""");
    }
}
