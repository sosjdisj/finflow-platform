package org.example.finflowbackend.settlement.service;

import org.example.finflowbackend.account.api.AccountApi;
import org.example.finflowbackend.account.api.AccountOwnerType;
import org.example.finflowbackend.account.api.AccountSnapshot;
import org.example.finflowbackend.ledger.api.LedgerApi;
import org.example.finflowbackend.settlement.SettlementStatus;
import org.example.finflowbackend.settlement.entity.SettlementOrder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 结算动账事务组件（S25 执行链路）。
 * 单事务：平台备付金出账 + 商户账户入账 + 两笔流水 + 推进 SUCCESS——动账与状态推进
 * 必须同一事务（铁律 3：钱动了单子必须落定，任一失败整体回滚，FAILED 重试不重不漏）。
 * 独立成 Bean：execute 编排不得同类调用本方法，否则 @Transactional 不生效（S16/S24 踩坑）。
 * 流水约定：bizType=SETTLEMENT、bizNo=settleNo，平台/商户两账户各一笔（uk_biz 含 account_id）；
 * 幂等由「状态机 CAS + 动账同事务」保证——失败重试时上次事务已整体回滚，不会重复动账。
 */
@Component
public class SettlementPostingService {

    /** 平台备付金账户 ownerId 约定为 0（与账务消费者一致）。 */
    private static final long PLATFORM_OWNER_ID = 0L;

    private final AccountApi accountApi;
    private final LedgerApi ledgerApi;
    private final SettlementStatusService statusService;

    public SettlementPostingService(AccountApi accountApi,
                                    LedgerApi ledgerApi,
                                    SettlementStatusService statusService) {
        this.accountApi = accountApi;
        this.ledgerApi = ledgerApi;
        this.statusService = statusService;
    }

    /** 执行打款：备付金 −应付额，商户账户 +应付额，全部成功后单子推 SUCCESS。 */
    @Transactional
    public void post(SettlementOrder order) {
        AccountSnapshot platform = accountApi.getOrCreate(
                AccountOwnerType.PLATFORM, PLATFORM_OWNER_ID, order.getCurrency());
        ledgerApi.debit(platform.id(), "SETTLEMENT", order.getSettleNo(), order.getTotalAmount(),
                "CONSOLE", "结算出账: " + order.getSettleNo());
        AccountSnapshot merchant = accountApi.getOrCreate(
                AccountOwnerType.MERCHANT, order.getMerchantId(), order.getCurrency());
        ledgerApi.credit(merchant.id(), "SETTLEMENT", order.getSettleNo(), order.getTotalAmount(),
                "CONSOLE", "结算入账: " + order.getSettleNo());
        statusService.transition(order.getId(), SettlementStatus.SUCCESS, "执行完成", "SYSTEM");
    }
}
