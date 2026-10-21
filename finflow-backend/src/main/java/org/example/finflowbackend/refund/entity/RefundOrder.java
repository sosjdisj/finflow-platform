package org.example.finflowbackend.refund.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 退款单（表 refund_order）。对应迁移脚本 V18__create_refund_order.sql。
 */
@Getter
@Setter
@TableName("refund_order")
public class RefundOrder {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 平台退款单号（平台生成，全局唯一）。 */
    private String refundNo;

    /** 支付订单ID。 */
    private Long orderId;

    /** 商户ID（冗余自支付订单，便于列表筛选）。 */
    private Long merchantId;

    /** 平台订单号（冗余自支付订单，展示免联表）。 */
    private String platformOrderNo;

    /** 本次退款金额（分）。 */
    private Long refundAmount;

    /** 币种，ISO 4217，随支付订单。 */
    private String currency;

    /** 退款原因。 */
    private String reason;

    /** 渠道编码（受理后回填）。 */
    private String channelCode;

    /** 渠道退款单号（受理后回填）。 */
    private String channelRefundNo;

    /** 状态，取值见 {@link org.example.finflowbackend.refund.RefundStatus}。 */
    private String status;

    /** 失败原因。 */
    private String failReason;

    /** 发起人。 */
    private String operator;

    /** 退款成功时间。 */
    private LocalDateTime refundedAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}