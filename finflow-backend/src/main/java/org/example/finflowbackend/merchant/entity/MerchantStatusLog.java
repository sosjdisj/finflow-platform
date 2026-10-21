package org.example.finflowbackend.merchant.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 商户状态变更记录（表 merchant_status_log）。只增不改的流水表。
 */
@Getter
@Setter
@TableName("merchant_status_log")
public class MerchantStatusLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long merchantId;

    private String fromStatus;

    private String toStatus;

    private String reason;

    private String operator;

    private LocalDateTime createdAt;
}
