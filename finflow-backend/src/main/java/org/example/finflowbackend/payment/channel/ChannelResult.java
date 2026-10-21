package org.example.finflowbackend.payment.channel;

/**
 * 渠道响应：accepted 表示渠道是否受理；
 * channelStatus 为渠道侧状态（PENDING/PROCESSING/PAID/FAILED 等），查询时回传。
 */
public record ChannelResult(boolean accepted, String channelOrderNo, String channelStatus, String message) {
}
