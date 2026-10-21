package org.example.finflowbackend.settlement.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 结算单（表 settlement_order）。同一商户同一结算日一张（uk 幂等）。
 * total_amount = Σ settlement_detail.merchant_income = Σ 清分单商户应得。
 */
@Getter
@Setter
@TableName("settlement_order")
public class SettlementOrder {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String settleNo;

    private Long merchantId;

    /** 结算日（清分单 settle_date）。 */
    private LocalDate settleDate;

    /** 清分笔数。 */
    private Integer orderCount;

    /** 应付商户总额（分）。 */
    private Long totalAmount;

    private String currency;

    private String status;

    private LocalDateTime generatedAt;

    private LocalDateTime calculatedAt;

    private LocalDateTime approvedAt;

    /** 审核人（登录名）。 */
    private String approvedBy;

    /** 执行完成（入账）时间。 */
    private LocalDateTime executedAt;

    /** 最近一次执行失败原因。 */
    private String failReason;
}
