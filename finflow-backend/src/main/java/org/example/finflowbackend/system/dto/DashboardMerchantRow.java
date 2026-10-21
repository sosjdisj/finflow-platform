package org.example.finflowbackend.system.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 商户交易额 TOP 行（JOIN merchant 取名称）。
 */
@Getter
@Setter
public class DashboardMerchantRow {

    private Long merchantId;

    private String merchantName;

    /** 支付金额合计（分）。 */
    private long amount;
}
