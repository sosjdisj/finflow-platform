package org.example.finflowbackend.system.config;

import java.util.List;
import java.util.Optional;

/**
 * 系统配置项唯一事实源（S37）：key / 值类型 / 默认值 / 描述集中在此定义，
 * 迁移脚本 V33 的 seed 与之保持一致（改动需两边同步）。
 * 配置项只允许在此登记，不支持运行时增删（防配置漂移）；管理页仅可改值与描述。
 */
public final class ConfigKeys {

    /** 值类型：数字。 */
    public static final String TYPE_NUMBER = "NUMBER";
    /** 值类型：字符串。 */
    public static final String TYPE_STRING = "STRING";
    /** 值类型：布尔（true/false）。 */
    public static final String TYPE_BOOLEAN = "BOOLEAN";
    /** 值类型：JSON。 */
    public static final String TYPE_JSON = "JSON";

    /** 配置项定义：key 全局唯一，type 决定管理页编辑控件与解析方式。 */
    public record Def(String key, String type, String defaultValue, String description) {
    }

    /** 全局单笔支付限额（分），下单金额超过直接拒绝。 */
    public static final Def PAYMENT_SINGLE_LIMIT =
            new Def("payment.amount.single-limit", TYPE_NUMBER, "10000000", "全局单笔支付限额（分），下单金额超过直接拒绝");

    /** 商户未配置费率时的平台默认费率（万分比）。 */
    public static final Def MERCHANT_RATE_DEFAULT_BPS =
            new Def("merchant.rate.default-bps", TYPE_NUMBER, "60", "商户未配置费率时的平台默认费率（万分比，60=0.6%）");

    /** 支付订单默认过期时间（小时）。 */
    public static final Def PAYMENT_ORDER_EXPIRE_HOURS =
            new Def("payment.order.expire-hours", TYPE_NUMBER, "2", "支付订单默认过期时间（小时），下单未传过期时间时使用");

    /** 风控人工审核阈值。 */
    public static final Def RISK_REVIEW_THRESHOLD =
            new Def("risk.review-threshold", TYPE_NUMBER, "30", "风控人工审核阈值：风险总分达到即转人工审核");

    /** 风控直接拒绝阈值。 */
    public static final Def RISK_REJECT_THRESHOLD =
            new Def("risk.reject-threshold", TYPE_NUMBER, "60", "风控直接拒绝阈值：风险总分达到即拒绝交易");

    /** Webhook 投递最大重试次数。 */
    public static final Def WEBHOOK_MAX_RETRIES =
            new Def("webhook.max-retries", TYPE_NUMBER, "5", "Webhook 投递最大重试次数，耗尽后等待手动重试");

    /** 结算单生成时间（每日 HH:mm）。 */
    public static final Def SETTLEMENT_GENERATE_TIME =
            new Def("settlement.generate-time", TYPE_STRING, "01:00", "结算单生成时间（每日 HH:mm），修改后次日生效");

    /** 支付下单总开关。 */
    public static final Def SWITCH_PAYMENT_CREATE =
            new Def("switch.payment.create", TYPE_BOOLEAN, "true", "支付下单总开关，关闭后拒绝新支付订单");

    /** 全部配置项（管理页列表与 seed 的口径）。 */
    public static final List<Def> ALL = List.of(
            PAYMENT_SINGLE_LIMIT, MERCHANT_RATE_DEFAULT_BPS, PAYMENT_ORDER_EXPIRE_HOURS,
            RISK_REVIEW_THRESHOLD, RISK_REJECT_THRESHOLD, WEBHOOK_MAX_RETRIES,
            SETTLEMENT_GENERATE_TIME, SWITCH_PAYMENT_CREATE);

    private ConfigKeys() {
    }

    /** 按 key 查定义；未登记的 key 返回 empty。 */
    public static Optional<Def> of(String key) {
        return ALL.stream().filter(def -> def.key().equals(key)).findFirst();
    }

    /** 值类型校验：非法值返回错误信息，合法返回 null。 */
    public static String validateValue(Def def, String value) {
        return switch (def.type()) {
            case TYPE_NUMBER -> value.matches("-?\\d+") ? null : "必须为整数";
            case TYPE_BOOLEAN -> value.equals("true") || value.equals("false") ? null : "必须为 true 或 false";
            case TYPE_JSON -> null;
            default -> value.isBlank() ? "不能为空" : null;
        };
    }

    /** key 列表（便于日志与调试输出）。 */
    public static List<String> keys() {
        return ALL.stream().map(Def::key).toList();
    }
}
