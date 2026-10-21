package org.example.finflowbackend.merchant.dto;

import org.example.finflowbackend.merchant.entity.MerchantSettleAccount;

import java.time.LocalDateTime;

/**
 * 结算账户信息：账号脱敏，只回后 4 位。
 */
public record SettleAccountResponse(Long id, Long merchantId, String accountType, String accountName,
                                    String accountNoMasked, String bankName,
                                    Integer isDefault, LocalDateTime createdAt) {

    public static SettleAccountResponse from(MerchantSettleAccount account) {
        String no = account.getAccountNo();
        String masked = no.length() <= 4 ? "****" : "****" + no.substring(no.length() - 4);
        return new SettleAccountResponse(account.getId(), account.getMerchantId(), account.getAccountType(),
                account.getAccountName(), masked, account.getBankName(), account.getIsDefault(),
                account.getCreatedAt());
    }
}
