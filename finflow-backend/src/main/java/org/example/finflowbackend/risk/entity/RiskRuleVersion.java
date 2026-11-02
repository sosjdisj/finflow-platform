package org.example.finflowbackend.risk.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 风控规则历史版本（表 finflow_risk.risk_rule_version，M10/V36）：每次规则变更留一行完整快照，只增不改。
 * 与 risk_rule 的写入在同一事务内完成；rule_id + version 唯一。
 */
@Getter
@Setter
@TableName(value = "risk_rule_version", schema = "finflow_risk")
public class RiskRuleVersion {

    /** 变更类型：迁移时存量规则回填的初始版本。 */
    public static final String CHANGE_INIT = "INIT";
    /** 变更类型：新增规则。 */
    public static final String CHANGE_CREATE = "CREATE";
    /** 变更类型：修改规则配置。 */
    public static final String CHANGE_UPDATE = "UPDATE";
    /** 变更类型：启停开关。 */
    public static final String CHANGE_TOGGLE = "TOGGLE";
    /** 变更类型：删除规则（历史保留）。 */
    public static final String CHANGE_DELETE = "DELETE";

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 规则ID（risk_rule.id，规则删除后历史仍保留）。 */
    private Long ruleId;

    private Integer version;

    private String ruleName;

    private String ruleType;

    /** 规则参数快照（JSON 串）。 */
    private String params;

    private Integer score;

    private Integer priority;

    private Boolean enabled;

    private String remark;

    /** 变更类型，取值见本类 CHANGE_* 常量。 */
    private String changeType;

    private LocalDateTime createdAt;
}
