package org.example.finflowbackend.merchant.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 商户阶梯费率（表 merchant_tier_rate）。1 商 N 档，amount_from 升序，
 * 清分时命中「amount_from ≤ 支付金额」的最大档；最低档应从 0 起步。
 */
@Getter
@Setter
@TableName("merchant_tier_rate")
public class MerchantTierRate {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long merchantId;

    /** 阶梯下限（分，含）。 */
    private Long amountFrom;

    /** 该档比例费率（万分比）。 */
    private Long rateBps;

    private LocalDateTime createdAt;
}
