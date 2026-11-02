package org.example.finflowbackend.account.api;

/**
 * account 模块对外能力（M03 facade）：余额变更与账户快照读取。
 * 其余模块只能经本接口动账，不得直接读 account 表。
 */
public interface AccountApi {

    /** 幂等建户并返回快照：同（主体类型, 主体ID, 币种）只建一次。 */
    AccountSnapshot getOrCreate(AccountOwnerType ownerType, Long ownerId, String currency);

    /** 账户快照，不存在抛 ACCOUNT_NOT_FOUND。 */
    AccountSnapshot getSnapshot(Long accountId);

    /** 入账：可用余额增加。 */
    AccountSnapshot credit(Long accountId, long amount);

    /** 扣减：可用余额减少，不足拒绝。 */
    AccountSnapshot debit(Long accountId, long amount);
}
