package org.example.finflowbackend.account;

/**
 * 账户主体类型。
 */
public enum AccountOwnerType {

    /** 商户。 */
    MERCHANT,

    /** 平台（备付金、收入等内部户）。 */
    PLATFORM,

    /** 渠道。 */
    CHANNEL
}
