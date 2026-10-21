package org.example.finflowbackend.risk.evaluate;

import org.example.finflowbackend.common.exception.BusinessException;
import org.example.finflowbackend.risk.RiskErrorCode;
import tools.jackson.databind.JsonNode;

/**
 * 规则参数读取小工具：各求值器共用，统一「必填/可选 + 默认值」的取参与报错口径。
 */
final class Params {

    private Params() {
    }

    /** 必填整型参数，缺失或非数字视为规则参数不合法。 */
    static long requireLong(JsonNode params, String field) {
        JsonNode node = params.get(field);
        if (node == null || !node.canConvertToLong()) {
            throw new BusinessException(RiskErrorCode.RULE_PARAMS_INVALID, "缺少必填参数 " + field);
        }
        return node.asLong();
    }
}
