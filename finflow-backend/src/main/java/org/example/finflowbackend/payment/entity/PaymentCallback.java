package org.example.finflowbackend.payment.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 渠道回调记录（表 payment_callback）。只增不改的流水表。
 * 结果 result：SUCCESS / FAILED。
 */
@Getter
@Setter
@TableName("payment_callback")
public class PaymentCallback {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long orderId;

    private String channelCode;

    private String channelOrderNo;

    private String result;

    private String payload;

    private LocalDateTime createdAt;
}
