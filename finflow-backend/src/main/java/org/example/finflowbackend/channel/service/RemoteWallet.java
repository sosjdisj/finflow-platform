package org.example.finflowbackend.channel.service;

import org.example.finflowbackend.channel.config.RemoteChannelProperties;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/** 远程钱包渠道（假渠道服务对接）：支持 ALIPAY / WECHAT，渠道码 MOCK_WALLET 沿用历史配置。 */
@Component
public class RemoteWallet extends RemoteChannel {

    public RemoteWallet(RemoteChannelProperties properties, ChannelSignatureService signatureService,
                        PaymentTransactionService transactionService, ObjectMapper objectMapper) {
        super("MOCK_WALLET", properties, signatureService, transactionService, objectMapper);
    }
}
