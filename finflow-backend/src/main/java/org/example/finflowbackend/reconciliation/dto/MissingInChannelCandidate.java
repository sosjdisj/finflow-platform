package org.example.finflowbackend.reconciliation.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 对账 MISSING_IN_CHANNEL 候选行：平台当日成功单未出现在账单中。
 * 是否属于本次对账渠道由 channel.api 批量判定，判定通过后回填渠道单号再批量写入结果表。
 */
@Getter
@Setter
public class MissingInChannelCandidate {

    private Long orderId;

    private String merchantOrderNo;

    /** 平台金额（分）。 */
    private Long platformAmount;

    private String platformStatus;

    private String platformOrderNo;

    /** 渠道单号：由调用方按 channel.api 查询结果回填。 */
    private String channelOrderNo;
}
