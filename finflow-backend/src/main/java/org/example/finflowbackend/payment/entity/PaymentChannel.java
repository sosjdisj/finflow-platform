package org.example.finflowbackend.payment.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 支付渠道配置（表 payment_channel）。对应迁移脚本 V12__create_payment_channel_and_transaction.sql。
 */
@Getter
@Setter
@TableName("payment_channel")
public class PaymentChannel {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 渠道编码，与渠道实现 Bean 的 {@code channelCode()} 一致。 */
    private String channelCode;

    private String channelName;

    /** 支持的支付方式，逗号分隔。 */
    private String paymentMethods;

    /** 渠道成本费率（万分比，S23）；NULL 视为 0。 */
    private Long costRateBps;

    /** 启停状态：true 启用。 */
    private Boolean enabled;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
