package org.example.finflowbackend.reconciliation.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 渠道账单暂存行（表 reconciliation_bill_item），只增不改；匹配在 S28。
 */
@Getter
@Setter
@TableName("reconciliation_bill_item")
public class ReconciliationBillItem {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long taskId;

    private Integer rowNo;

    private String channelOrderNo;

    private String merchantOrderNo;

    /** 金额（分）。 */
    private Long amount;

    private String billStatus;

    private LocalDate billDate;

    private LocalDateTime createdAt;
}
