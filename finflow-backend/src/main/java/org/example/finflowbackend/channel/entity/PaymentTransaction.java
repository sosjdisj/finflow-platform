package org.example.finflowbackend.channel.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 渠道交互记录（表 finflow_channel.payment_transaction）。只增不改的流水表。
 * 交互类型 txn_type：PAYMENT / REFUND / QUERY / REFUND_QUERY。M07 起归属渠道库 finflow_channel。
 */
@Getter
@Setter
@TableName(value = "payment_transaction", schema = "finflow_channel")
public class PaymentTransaction {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long orderId;

    private String channelCode;

    private String txnType;

    /** 渠道单号（渠道受理后返回）。 */
    private String channelOrderNo;

    private String requestPayload;

    private String responsePayload;

    private LocalDateTime createdAt;
}
