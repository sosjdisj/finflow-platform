package org.example.finflowbackend.refund.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 退款单状态变更记录（表 refund_order_state_log），只增不改。
 */
@Getter
@Setter
@TableName("refund_order_state_log")
public class RefundOrderStateLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 退款单ID。 */
    private Long refundId;

    private String fromStatus;

    private String toStatus;

    private String reason;

    private String operator;

    private LocalDateTime createdAt;
}