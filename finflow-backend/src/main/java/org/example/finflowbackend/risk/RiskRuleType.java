package org.example.finflowbackend.risk;

import java.util.Locale;

/**
 * 内置规则类型。新增类型（设备、地域等）时：加枚举值 + 对应 {@code RuleEvaluator} 实现即可接入求值器。
 */
public enum RiskRuleType {

    /** 单笔金额限额：params {amountLimit}（分），订单金额超过限额命中。 */
    AMOUNT_LIMIT,
    /** 时间窗口内支付频次：params {windowSeconds, maxCount}，Redis 计数。 */
    FREQUENCY,
    /** 黑名单：params {dimension: USER/IP/DEVICE}，命中直接拒绝（不累分）。 */
    BLACKLIST,
    /** 同 IP 多账户：params {maxAccounts, windowSeconds}，窗口内同 IP 支付的商户数超限命中（Redis SET）。 */
    SAME_IP_MULTI_ACCOUNT;

    public static RiskRuleType parse(String value) {
        try {
            return RiskRuleType.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException | NullPointerException e) {
            return null;
        }
    }
}
