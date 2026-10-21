package org.example.finflowbackend.account.dto;

import org.example.finflowbackend.account.entity.Account;

import java.time.LocalDateTime;

/**
 * 账户视图：余额一律为分（bigint），前端展示时再换算。
 */
public record AccountResponse(Long id, String ownerType, Long ownerId, String currency,
                              Long availableBalance, Long frozenBalance, LocalDateTime createdAt) {

    public static AccountResponse from(Account account) {
        return new AccountResponse(account.getId(), account.getOwnerType(), account.getOwnerId(),
                account.getCurrency(), account.getAvailableBalance(), account.getFrozenBalance(),
                account.getCreatedAt());
    }
}
