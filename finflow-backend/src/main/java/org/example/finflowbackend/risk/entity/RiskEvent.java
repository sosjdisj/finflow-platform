package org.example.finflowbackend.risk.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 风险事件（表 finflow_risk.risk_event）：每次风控检查留痕，只增不改。
 * 由 V27 建表，M10/V36 起归属风控库 finflow_risk。
 * 订单要素与命中规则明细均快照落库，订单后续状态变化不影响本记录。
 */
@Getter
@Setter
@TableName(value = "risk_event", schema = "finflow_risk")
public class RiskEvent {

    /** 判决：放行。 */
    public static final String VERDICT_ALLOW = "ALLOW";
    /** 判决：待人工审核（订单进 REVIEW）。 */
    public static final String VERDICT_REVIEW = "REVIEW";
    /** 判决：拒绝（订单置 FAILED）。 */
    public static final String VERDICT_REJECT = "REJECT";

    @TableId(type = IdType.AUTO)
    private Long id;

    private String eventNo;

    private Long orderId;

    private Long merchantId;

    /** 订单金额（分）。 */
    private Long amount;

    private String clientIp;

    private String deviceId;

    /** 命中规则明细快照（JSON 数组：名称/类型/分值/说明）。 */
    private String hitRules;

    /** 累加风险总分。 */
    private Integer totalScore;

    private String verdict;

    private LocalDateTime createdAt;
}
