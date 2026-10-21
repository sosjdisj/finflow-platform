package org.example.finflowbackend.clearing.dto;

import org.example.finflowbackend.clearing.entity.ClearingDetail;

/**
 * 分账明细行（每张清分单固定 3 行，方向由 partyType 表达）。
 */
public record ClearingDetailResponse(Long id, String partyType, Long partyId, String channelCode,
                                     Long amount, String remark) {

    public static ClearingDetailResponse from(ClearingDetail detail) {
        return new ClearingDetailResponse(detail.getId(), detail.getPartyType(), detail.getPartyId(),
                detail.getChannelCode(), detail.getAmount(), detail.getRemark());
    }
}
