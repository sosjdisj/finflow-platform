package org.example.finflowbackend.risk.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 风控黑名单（表 risk_blacklist）：用户 / IP / 设备三维度，维度 + 值唯一。
 * 删除即解除拉黑，不做软删除。
 */
@Getter
@Setter
@TableName("risk_blacklist")
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
