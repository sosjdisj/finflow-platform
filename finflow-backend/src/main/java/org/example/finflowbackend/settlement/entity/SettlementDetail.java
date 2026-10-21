package org.example.finflowbackend.settlement.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 结算单明细（表 settlement_detail）：逐笔清分引用，金额从清分单复制快照。
 */
@Getter
@Setter
@TableName("settlement_detail")
public class SettlementDetail {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long settlementId;

    private Long clearingId;

    private Long orderId;

    /** 支付金额（分）。 */
    private Long payAmount;

    /** 该笔商户应得（分）。 */
    private Long merchantIncome;

    private LocalDateTime createdAt;
}
