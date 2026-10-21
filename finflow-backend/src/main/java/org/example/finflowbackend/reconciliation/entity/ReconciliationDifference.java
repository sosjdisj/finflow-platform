package org.example.finflowbackend.reconciliation.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 对账差异单（表 reconciliation_difference）。
 * 由人工在对账结果页对非 MATCHED 记录发起，关键字段快照自匹配记录；
 * 匹配重跑清重建记录后，差异单凭快照仍完整可读（record_id 仅留痕）。
 */
@Getter
@Setter
@TableName("reconciliation_difference")
public class ReconciliationDifference {

    /** 调账：走 ledger 模块调账入口（S19 流水入口，ADJUSTMENT 类型流水）。 */
    public static final String HANDLE_ADJUST = "ADJUST";
    /** 补单：人工补录平台订单后登记凭证。 */
    public static final String HANDLE_SUPPLEMENT = "SUPPLEMENT";

    @TableId(type = IdType.AUTO)
    private Long id;

    private String diffNo;

    private Long taskId;

    private Long recordId;

    private String diffType;

    private String status;

    private String channelOrderNo;

    private String merchantOrderNo;

    private Long billAmount;

    private Long platformAmount;

    private String billStatus;

    private String platformStatus;

    private String platformOrderNo;

    private String remark;

    private String handleType;

    private String handleRemark;

    private String handleResult;

    private String ledgerNo;

    private String confirmedBy;

    private LocalDateTime confirmedAt;

    private String handledBy;

    private LocalDateTime handledAt;

    private String createdBy;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
