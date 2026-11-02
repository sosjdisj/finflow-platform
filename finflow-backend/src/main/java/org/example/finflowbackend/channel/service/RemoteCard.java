package org.example.finflowbackend.channel.service;

import org.example.finflowbackend.channel.config.RemoteChannelProperties;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/** 远程快捷卡渠道（假渠道服务对接）：支持 QUICK_CARD，渠道码 MOCK_CARD 沿用历史配置。 */
@Component
public class RemoteCard extends RemoteChannel {

    public RemoteCard(RemoteChannelProperties properties, ChannelSignatureService signatureService,
                      PaymentTransactionService transactionService, ObjectMapper objectMapper) {
        super("MOCK_CARD", properties, signatureService, transactionService, objectMapper);
    }
}
