package org.example.finflowbackend.clearing.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 分账明细（表 clearing_detail）。每张清分单固定 3 行，金额均为正数，
 * 资金方向由 party_type 表达：MERCHANT（商户应得）/ PLATFORM（平台手续费）/ CHANNEL（渠道成本）。
 */
@Getter
@Setter
@TableName("clearing_detail")
public class ClearingDetail {

    public static final String PARTY_MERCHANT = "MERCHANT";
    public static final String PARTY_PLATFORM = "PLATFORM";
    public static final String PARTY_CHANNEL = "CHANNEL";

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long clearingId;

    /** 分账方向：MERCHANT/PLATFORM/CHANNEL。 */
    private String partyType;

    /** 主体ID：MERCHANT=商户ID / PLATFORM=0 / CHANNEL=NULL。 */
    private Long partyId;

    /** 渠道编码（CHANNEL 行填）。 */
    private String channelCode;

    /** 分账金额（分，正数）。 */
    private Long amount;

    /** 说明（费率口径等）。 */
    private String remark;

    private LocalDateTime createdAt;
}
