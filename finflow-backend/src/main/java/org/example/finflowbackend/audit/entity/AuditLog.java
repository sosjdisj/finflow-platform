package org.example.finflowbackend.audit.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 审计日志（表 audit_log）。对应迁移脚本 V32__create_audit_log.sql。
 * 只增不改：无 update/delete 入口，禁止 MP 逻辑删除等填充。
 */
@Getter
@Setter
@TableName("audit_log")
public class AuditLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long actorId;

    private String actorUsername;

    /** 操作动作（如 MERCHANT_RATE_SET）。 */
    private String action;

    private String resourceType;

    private String resourceId;

    /** 变更前快照（JSON，首次设置为 NULL）。 */
    private String beforeSnapshot;

    /** 变更后快照（JSON）。 */
    private String afterSnapshot;

    /** 结果 SUCCESS/FAILED。 */
    private String result;

    private String errorMessage;

    private String clientIp;

    private LocalDateTime createdAt;
}
