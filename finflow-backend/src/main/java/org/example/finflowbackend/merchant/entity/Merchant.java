package org.example.finflowbackend.merchant.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 商户（表 merchant）。对应迁移脚本 V7__create_merchant_tables.sql。
 */
@Getter
@Setter
@TableName("merchant")
public class Merchant {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 商户编号（平台生成）。 */
    private String merchantNo;

    private String merchantName;

    private String category;

    private String contactName;

    private String contactPhone;

    private String contactEmail;

    /** 统一社会信用代码。 */
    private String businessLicenseNo;

    private String businessAddress;

    /** 结算周期：T1 / D1。 */
    private String settleCycle;

    /** Webhook 回调地址，阶段 9 投递时使用。 */
    private String webhookUrl;

    /** 状态，取值见 {@link org.example.finflowbackend.merchant.MerchantStatus}。 */
    private String status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
