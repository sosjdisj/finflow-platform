package org.example.finflowbackend.reconciliation.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 对账任务（表 reconciliation_task）。
 */
@Getter
@Setter
@TableName("reconciliation_task")
public class ReconciliationTask {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String taskNo;

    private String channelCode;

    private LocalDate billDate;

    private String fileName;

    private String filePath;

    private String status;

    private Integer totalRows;

    private Integer validRows;

    private String errorMessage;

    private String uploadedBy;

    private LocalDateTime matchedAt;

    private Integer matchedCount;

    private Integer amountMismatchCount;

    private Integer statusMismatchCount;

    private Integer missingInPlatformCount;

    private Integer missingInChannelCount;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
