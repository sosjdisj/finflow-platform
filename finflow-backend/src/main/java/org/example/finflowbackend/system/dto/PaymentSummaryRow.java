package org.example.finflowbackend.system.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 当日支付侧汇总行（Dashboard 统计 XML 映射载体）。
 */
@Getter
@Setter
public class PaymentSummaryRow {

    /** 当日创建订单总数（含全部状态）。 */
    private long totalOrders;

    /** 终态成功单数（PAID/SETTLED/REFUNDING/REFUNDED）。 */
    private long successOrders;

    /** 终态成功金额合计（分）。 */
    private long successAmount;

    /** 异常单数（FAILED/CLOSED）。 */
    private long abnormalOrders;
}
