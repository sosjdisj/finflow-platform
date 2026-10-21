package org.example.finflowbackend.payment.channel;

import org.example.finflowbackend.payment.service.PaymentTransactionService;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/** 远程网银渠道（假渠道服务对接）：支持 BANK_CARD，渠道码 MOCK_BANK 沿用历史配置。 */
@Component
public class RemoteBank extends RemoteChannel {

    public RemoteBank(RemoteChannelProperties properties, ChannelSignatureService signatureService,
                      PaymentTransactionService transactionService, ObjectMapper objectMapper) {
        super("MOCK_BANK", properties, signatureService, transactionService, objectMapper);
    }
}
