package org.example.finflowbackend.payment.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.example.finflowbackend.payment.entity.PaymentChannel;
import org.example.finflowbackend.payment.mapper.PaymentChannelMapper;
import org.springframework.stereotype.Service;

/**
 * 渠道运营配置查询（S23）：清分需要的渠道成本费率等。
 * 渠道成本是平台与渠道侧的结算口径，与商户费率无关。
 */
@Service
public class ChannelConfigService {

    private final PaymentChannelMapper channelMapper;

    public ChannelConfigService(PaymentChannelMapper channelMapper) {
        this.channelMapper = channelMapper;
    }

    /** 渠道成本费率（万分比）；渠道不存在或未配置返回 null（调用方按 0 处理）。 */
    public Long getCostRateBps(String channelCode) {
        PaymentChannel channel = selectChannel(channelCode);
        return channel == null ? null : channel.getCostRateBps();
    }

    /** 渠道是否启用（S27 对账上传校验用）。 */
    public boolean existsEnabled(String channelCode) {
        PaymentChannel channel = selectChannel(channelCode);
        return channel != null && channel.getEnabled();
    }

    private PaymentChannel selectChannel(String channelCode) {
        return channelMapper.selectOne(
                Wrappers.<PaymentChannel>query().eq("channel_code", channelCode));
    }
}
