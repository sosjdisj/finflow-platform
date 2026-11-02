package org.example.finflowbackend.channel.api;

/**
 * 渠道运营配置查询的对外能力：清分、对账读取渠道成本费率与启用状态。
 */
public interface ChannelConfigApi {

    /** 渠道成本费率（万分比）；渠道不存在或未配置返回 null（调用方按 0 处理）。 */
    Long getCostRateBps(String channelCode);

    /** 渠道是否启用。 */
    boolean existsEnabled(String channelCode);
}
