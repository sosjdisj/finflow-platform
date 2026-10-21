package org.example.finflowbackend.system.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 渠道占比行（clearing_order 按渠道聚合）。
 */
@Getter
@Setter
public class DashboardChannelRow {

    private String channelCode;

    /** 支付金额合计（分）。 */
    private long amount;
}
