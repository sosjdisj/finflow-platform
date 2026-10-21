package org.example.finflowbackend.clearing.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 清分单（表 clearing_order）。一笔支付成功订单一张，清分即终态 CLEARED。
 * 三方向金额恒等式：merchant_income + platform_fee + channel_cost = pay_amount。
 */
@Getter
@Setter
@TableName("clearing_order")
public class ClearingOrder {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 支付订单ID（uk，防重复清分）。 */
    private Long orderId;

    private Long merchantId;

    private String platformOrderNo;

    /** 支付金额（分）。 */
    private Long payAmount;

    /** 商户应得（分）。 */
    private Long merchantIncome;

    /** 平台手续费（分）。 */
    private Long platformFee;

    /** 渠道成本（分）。 */
    private Long channelCost;

    private String currency;

    /** 实际收款渠道编码。 */
    private String channelCode;

    /** 支付成功日（结算按商户+日期汇总的维度）。 */
    private LocalDate settleDate;

    /** 清分即终态。 */
    private String status;

    private LocalDateTime clearedAt;
}
