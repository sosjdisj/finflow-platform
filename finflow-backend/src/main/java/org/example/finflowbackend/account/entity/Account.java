package org.example.finflowbackend.account.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 账户与余额（表 account）。对应迁移脚本 V15__create_account.sql。
 * 所有资金变更只能经 {@link org.example.finflowbackend.account.service.AccountService} 统一入口。
 */
@Getter
@Setter
@TableName("account")
public class Account {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 主体类型，取值见 {@link org.example.finflowbackend.account.AccountOwnerType}。 */
    private String ownerType;

    /** 主体ID（平台账户固定为 0）。 */
    private Long ownerId;

    /** 币种，ISO 4217。 */
    private String currency;

    /** 可用余额（分）。 */
    private Long availableBalance;

    /** 冻结余额（分）。 */
    private Long frozenBalance;

    /** 乐观锁版本号，配合行锁做并发双保险。 */
    @Version
    private Integer version;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
