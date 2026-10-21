package org.example.finflowbackend.system.file;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 文件元数据（表 file_object，S38）：对象本体在 MinIO，本表只记 object_key 与元数据。
 * 只增不改：无更新、无逻辑删除（V34 注释同步维护）。
 */
@Getter
@Setter
@TableName("file_object")
public class FileObject {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 对象键（唯一，如 reconciliation/20261018/uuid.csv）。 */
    private String objectKey;

    private String bucket;

    /** 原始文件名（下载回显）。 */
    private String fileName;

    private String contentType;

    private Long sizeBytes;

    /** 业务类型：FileBizType name。 */
    private String bizType;

    /** 关联业务 ID（可空；对账文件靠 task.file_path 反向关联）。 */
    private Long bizId;

    /** 上传人用户 ID（0=系统）。 */
    private Long uploaderId;

    private LocalDateTime createdAt;
}
