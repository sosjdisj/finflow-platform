package org.example.finflowbackend.channel.api;

/**
 * 渠道响应：accepted 表示渠道是否受理；
 * channelCode 为实际处理本次交互的渠道码（调用方落库 / 判断渠道用）；
 * channelStatus 为渠道侧状态（PENDING/PROCESSING/PAID/FAILED 等），查询时回传。
 */
public record ChannelResult(String channelCode, boolean accepted, String channelOrderNo,
                            String channelStatus, String message) {
}
