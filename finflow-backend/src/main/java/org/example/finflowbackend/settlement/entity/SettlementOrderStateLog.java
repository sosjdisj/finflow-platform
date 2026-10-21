package org.example.finflowbackend.settlement.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 结算单状态变更记录（表 settlement_order_state_log），只增不改。
 */
@Getter
@Setter
@TableName("settlement_order_state_log")
public class SettlementOrderStateLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 结算单ID。 */
    private Long settlementId;

    private String fromStatus;

    private String toStatus;

    private String reason;

    private String operator;

    private LocalDateTime createdAt;
}
