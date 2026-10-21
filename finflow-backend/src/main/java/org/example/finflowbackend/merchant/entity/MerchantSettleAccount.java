package org.example.finflowbackend.merchant.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 商户结算账户（表 merchant_settle_account）。仅信息管理，不动资金。
 */
@Getter
@Setter
@TableName("merchant_settle_account")
public class MerchantSettleAccount {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long merchantId;

    /** 类型：BANK_CARD / ALIPAY / WECHAT。 */
    private String accountType;

    private String accountName;

    private String accountNo;

    private String bankName;

    private Integer isDefault;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
