package org.example.finflowbackend.risk.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 风控规则（表 risk_rule）。对应迁移脚本 V27__create_risk_tables.sql。
 * params 为按类型定义的 JSON 串，由对应求值器解析校验。
 */
@Getter
@Setter
@TableName("risk_rule")
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

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
