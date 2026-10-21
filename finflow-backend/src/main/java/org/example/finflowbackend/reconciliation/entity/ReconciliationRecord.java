package org.example.finflowbackend.reconciliation.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 对账匹配结果（表 reconciliation_record）。
 * 由任务级批量匹配 SQL 一次性产出；重跑匹配前先清旧记录，表内数据始终对应当次匹配。
 */
@Getter
@Setter
@TableName("reconciliation_record")
public class ReconciliationRecord {

    /** 匹配一致。 */
    public static final String MATCHED = "MATCHED";
    /** 金额不一致。 */
    public static final String AMOUNT_MISMATCH = "AMOUNT_MISMATCH";
    /** 状态不一致。 */
    public static final String STATUS_MISMATCH = "STATUS_MISMATCH";
    /** 渠道有、平台无。 */
    public static final String MISSING_IN_PLATFORM = "MISSING_IN_PLATFORM";
    /** 平台有、渠道无。 */
    public static final String MISSING_IN_CHANNEL = "MISSING_IN_CHANNEL";

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long taskId;

    private String result;

    private Long billItemId;

    private String channelOrderNo;

    private String merchantOrderNo;

    private Long billAmount;

    private String billStatus;

    private Long platformAmount;

    private String platformStatus;

    private String platformOrderNo;

    private String platformRefundNo;

    private String remark;

    private LocalDateTime createdAt;
}
