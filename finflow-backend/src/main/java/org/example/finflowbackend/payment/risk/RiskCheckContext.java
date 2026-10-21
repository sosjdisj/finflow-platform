package org.example.finflowbackend.payment.risk;

import java.util.Set;

/**
 * 交易风控检查记录：订单要素快照，供风控实现判断。
 * clientIp / deviceId 为可选要素（黑名单、同 IP 多账户等规则使用，缺失时相关规则跳过）。
 */
public record RiskCheckContext(Long orderId, Long merchantId, long amount, String paymentMethod,
                               String clientIp, String deviceId) {

    /** 判决三态：放行 / 待人工审核（订单进 REVIEW）/ 拒绝（reason 为拦截原因）。 */
    public enum Verdict {
        ALLOW, REVIEW, REJECT;

        public static final Set<Verdict> ALL = Set.of(values());
    }

    /** 判决结果：REVIEW/REJECT 时 reason 为原因说明。 */
    public record Decision(Verdict verdict, String reason) {

        public static Decision allow() {
            return new Decision(Verdict.ALLOW, null);
        }

        public static Decision review(String reason) {
            return new Decision(Verdict.REVIEW, reason);
        }

        public static Decision reject(String reason) {
            return new Decision(Verdict.REJECT, reason);
        }
    }
}
