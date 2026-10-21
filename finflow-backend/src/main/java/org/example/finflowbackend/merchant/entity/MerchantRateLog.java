package org.example.finflowbackend.merchant.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 商户费率变更历史（表 merchant_rate_log）。只增不改的流水表。
 */
@Getter
@Setter
@TableName("merchant_rate_log")
public class MerchantRateLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long merchantId;

    private Long oldRateBps;

    private Long oldFixedFee;

    private Long newRateBps;

    private Long newFixedFee;

    private String operator;

    private LocalDateTime createdAt;
}
