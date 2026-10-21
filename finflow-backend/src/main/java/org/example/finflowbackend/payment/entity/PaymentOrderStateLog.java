package org.example.finflowbackend.payment.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 支付订单状态变更记录（表 payment_order_state_log）。只增不改的流水表。
 */
@Getter
@Setter
@TableName("payment_order_state_log")
public class PaymentOrderStateLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long orderId;

    private String fromStatus;

    private String toStatus;

    private String reason;

    private String operator;

    private LocalDateTime createdAt;
}
