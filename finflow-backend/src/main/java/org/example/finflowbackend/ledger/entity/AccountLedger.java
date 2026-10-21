package org.example.finflowbackend.ledger.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 资金流水（表 account_ledger）。对应迁移脚本 V16__create_account_ledger.sql。
 * 只增不改：无 updated_at、无逻辑删除，代码层只提供 insert 与查询。
 */
@Getter
@Setter
@TableName("account_ledger")
public class AccountLedger {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long accountId;

    /** 业务类型：PAYMENT/SETTLEMENT/ADJUSTMENT。 */
    private String bizType;

    /** 业务单号（如平台订单号）。 */
    private String bizNo;

    /** 变动金额（分，带符号：正=入账，负=出账）。 */
    private Long amount;

    /** 变动前可用余额（分）。 */
    private Long balanceBefore;

    /** 变动后可用余额（分）。 */
    private Long balanceAfter;

    /** 币种，ISO 4217。 */
    private String currency;

    /** 来源：PAYMENT_EVENT/CONSOLE/TASK。 */
    private String source;

    /** 备注（调账原因等，可空）。 */
    private String remark;

    private LocalDateTime createdAt;
}
