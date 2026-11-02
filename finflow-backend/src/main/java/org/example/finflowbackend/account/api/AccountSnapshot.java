package org.example.finflowbackend.account.api;

import org.example.finflowbackend.account.entity.Account;

/**
 * 账户余额快照：跨模块只传快照，不传账户实体（M03 边界约定）。
 */
public record AccountSnapshot(Long id, String ownerType, Long ownerId, String currency,
                              long availableBalance, long frozenBalance) {

    public static AccountSnapshot from(Account account) {
        return new AccountSnapshot(account.getId(), account.getOwnerType(), account.getOwnerId(),
                account.getCurrency(), account.getAvailableBalance(), account.getFrozenBalance());
    }
}
