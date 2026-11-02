package org.example.finflowbackend.openapi.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 商户 OpenAPI 配额策略（表 merchant_openapi_quota，M12）。
 * 表只存策略，实际计数在 Redis；qps_limit / daily_limit 为 0 表示沿用平台默认值。
 */
@Getter
@Setter
@TableName("merchant_openapi_quota")
public class MerchantOpenApiQuota {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long merchantId;

    /** 接口编码，取值见 {@link org.example.finflowbackend.openapi.quota.OpenApiApiCode}。 */
    private String apiCode;

    private Integer qpsLimit;

    private Long dailyLimit;

    /** 是否开通：false 表示该商户该接口停用。 */
    private Boolean enabled;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
