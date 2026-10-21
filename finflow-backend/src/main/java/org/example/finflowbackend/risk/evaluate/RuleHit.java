package org.example.finflowbackend.risk.evaluate;

/**
 * 单条规则的命中结果：hit=false 时 detail 为空；hit=true 时 detail 为写入风险事件的人读说明。
 */
public record RuleHit(boolean hit, String detail) {

    public static RuleHit miss() {
        return new RuleHit(false, null);
    }

    public static RuleHit hit(String detail) {
        return new RuleHit(true, detail);
    }
}
