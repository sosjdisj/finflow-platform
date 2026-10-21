package org.example.finflowbackend.payment.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 支付订单（表 payment_order）。对应迁移脚本 V11__create_payment_order_tables.sql。
 */
@Getter
@Setter
@TableName("payment_order")
public class PaymentOrder {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long merchantId;

    /** 商户订单号（商户侧传入，商户内唯一）。 */
    private String merchantOrderNo;

    /** 平台订单号（平台生成，全局唯一）。 */
    private String platformOrderNo;

    /** 订单金额（分）。 */
    private Long amount;

    /** 已申请退款累计金额（分）：申请占用、失败释放，S21 防超退。 */
    private Long refundedAmount;

    /** 币种，ISO 4217。 */
    private String currency;

    /** 商品标题。 */
    private String subject;

    /** 商品描述。 */
    private String body;

    /** 支付方式。 */
    private String paymentMethod;

    /** 回调地址。 */
    private String callbackUrl;

    /** 订单过期时间。 */
    private LocalDateTime expiredAt;

    /** 状态，取值见 {@link org.example.finflowbackend.payment.PaymentStatus}。 */
    private String status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
