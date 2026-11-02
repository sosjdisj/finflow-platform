package org.example.finflowbackend.risk.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 风控规则（表 finflow_risk.risk_rule）。由 V27 建表，M10/V36 起归属风控库 finflow_risk。
 * params 为按类型定义的 JSON 串，由对应求值器解析校验；
 * version 为当前版本号，每次变更 +1，历史快照见 {@link RiskRuleVersion}。
 */
@Getter
@Setter
@TableName(value = "risk_rule", schema = "finflow_risk")
public class RiskRule {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String ruleName;

    /** 规则类型，取值见 {@link org.example.finflowbackend.risk.RiskRuleType}。 */
    private String ruleType;

    /** 规则参数（JSON 串，按类型定义）。 */
    private String params;

    /** 命中加的风险分（黑名单类型命中直接拒绝，分值仅留档）。 */
    private Integer score;

    /** 执行优先级，小者先执行。 */
    private Integer priority;

    private Boolean enabled;

    private String remark;

    /** 当前版本号：每次变更 +1，与 risk_rule_version.version 对应。 */
    private Integer version;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
