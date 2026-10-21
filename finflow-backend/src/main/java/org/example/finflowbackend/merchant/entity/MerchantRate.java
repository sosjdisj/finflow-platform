package org.example.finflowbackend.merchant.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 商户费率配置（表 merchant_rate）。对应迁移脚本 V9__create_merchant_rate_and_settle_account.sql。
 * 比例费率万分比整数、固定手续费分；阶梯费率留待清分阶段扩展。
 */
@Getter
@Setter
@TableName("merchant_rate")
public class MerchantRate {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long merchantId;

    /** 比例费率（万分比），NULL 不启用。 */
    private Long rateBps;

    /** 固定手续费（分），NULL 不启用。 */
    private Long fixedFee;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
