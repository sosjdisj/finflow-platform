package org.example.finflowbackend.risk.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 风控黑名单（表 finflow_risk.risk_blacklist）：用户 / IP / 设备三维度，维度 + 值唯一。
 * 由 V27 建表，M10/V36 起归属风控库 finflow_risk。
 * 删除即解除拉黑，不做软删除。
 */
@Getter
@Setter
@TableName(value = "risk_blacklist", schema = "finflow_risk")
public class RiskBlacklist {

    public static final String DIMENSION_USER = "USER";
    public static final String DIMENSION_IP = "IP";
    public static final String DIMENSION_DEVICE = "DEVICE";

    @TableId(type = IdType.AUTO)
    private Long id;

    private String dimension;

    private String value;

    private String reason;

    private String createdBy;

    private LocalDateTime createdAt;
}
