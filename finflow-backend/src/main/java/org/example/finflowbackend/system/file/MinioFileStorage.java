package org.example.finflowbackend.system.file;

import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import jakarta.annotation.PostConstruct;
import org.example.finflowbackend.common.exception.BusinessException;
import org.example.finflowbackend.system.SystemErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.InputStream;

/**
 * MinIO 对象存储实现（S38）。启动时确保 bucket 存在（不存在则创建）；
 * put/get 均把 SDK 异常包装为统一业务异常，调用方无需感知 MinIO。
 */
@Component
public class MinioFileStorage implements FileStorage {

    private static final Logger log = LoggerFactory.getLogger(MinioFileStorage.class);

    private final MinioClient client;
    private final String bucket;

    public MinioFileStorage(StorageProperties properties) {
        this.bucket = properties.bucket();
        this.client = MinioClient.builder()
                .endpoint(properties.endpoint())
                .credentials(properties.accessKey(), properties.secretKey())
                .build();
    }

    /** 启动即确保 bucket 就绪：缺桶只告警不阻断启动（上传时会再失败可见），避免中间件未就绪拖死应用。 */
    @PostConstruct
    void ensureBucket() {
        try {
            if (!client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())) {
                client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
                log.info("[文件中心] 已创建 bucket: {}", bucket);
            }
        } catch (Exception e) {
            log.warn("[文件中心] bucket 检查失败（稍后上传时可见具体错误）: bucket={}, 原因={}", bucket, e.getMessage());
        }
    }

    @Override
    public void put(String objectKey, InputStream in, long size, String contentType) {
        try {
            client.putObject(PutObjectArgs.builder()
                    .bucket(bucket).object(objectKey)
                    .stream(in, size, -1)
                    .contentType(contentType)
                    .build());
        } catch (Exception e) {
            log.error("[文件中心] 对象上传失败: key={}", objectKey, e);
            throw new BusinessException(SystemErrorCode.FILE_STORE_FAILED, "对象上传失败");
        }
    }

    @Override
    public InputStream get(String objectKey) {
        try {
            return client.getObject(GetObjectArgs.builder().bucket(bucket).object(objectKey).build());
        } catch (Exception e) {
            log.error("[文件中心] 对象读取失败: key={}", objectKey, e);
            throw new BusinessException(SystemErrorCode.FILE_STORE_FAILED, "对象读取失败");
        }
    }
}
