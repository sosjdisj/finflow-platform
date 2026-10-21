package org.example.finflowbackend.system.file;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.example.finflowbackend.common.exception.BusinessException;
import org.example.finflowbackend.system.SystemErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;

/** 文件中心实现（S38）：objectKey = {bizType小写}/{yyyyMMdd}/{uuid}{扩展名}，全局唯一不依赖库自增。 */
@Service
public class FileObjectServiceImpl implements FileObjectService {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyyMMdd", Locale.ROOT);

    private final FileStorage storage;
    private final FileObjectMapper fileObjectMapper;
    private final String bucket;

    public FileObjectServiceImpl(FileStorage storage, FileObjectMapper fileObjectMapper,
                                 StorageProperties storageProperties) {
        this.storage = storage;
        this.fileObjectMapper = fileObjectMapper;
        this.bucket = storageProperties.bucket();
    }

    @Override
    public FileObjectResponse upload(MultipartFile file, String bizType, Long bizId, long uploaderId) {
        FileBizType type = FileBizType.parse(bizType);
        if (file == null || file.isEmpty()) {
            throw new BusinessException(SystemErrorCode.FILE_EMPTY);
        }
        String fileName = file.getOriginalFilename() == null ? "unnamed" : file.getOriginalFilename();
        String objectKey = buildObjectKey(type, fileName);

        try (InputStream in = file.getInputStream()) {
            storage.put(objectKey, in, file.getSize(), file.getContentType());
        } catch (Exception e) {
            if (e instanceof BusinessException business) {
                throw business;
            }
            throw new BusinessException(SystemErrorCode.FILE_STORE_FAILED, "读取上传内容失败");
        }

        FileObject fileObject = new FileObject();
        fileObject.setObjectKey(objectKey);
        fileObject.setBucket(bucket);
        fileObject.setFileName(fileName);
        fileObject.setContentType(file.getContentType() == null ? "application/octet-stream" : file.getContentType());
        fileObject.setSizeBytes(file.getSize());
        fileObject.setBizType(type.name());
        fileObject.setBizId(bizId);
        fileObject.setUploaderId(uploaderId);
        fileObjectMapper.insert(fileObject);
        return FileObjectResponse.from(fileObject);
    }

    @Override
    public FileObjectResponse getMeta(long id) {
        return FileObjectResponse.from(requireFile(id));
    }

    @Override
    public InputStream openStream(long id) {
        FileObject fileObject = requireFile(id);
        return storage.get(fileObject.getObjectKey());
    }

    private FileObject requireFile(long id) {
        FileObject fileObject = fileObjectMapper.selectById(id);
        if (fileObject == null) {
            throw new BusinessException(SystemErrorCode.FILE_NOT_FOUND);
        }
        return fileObject;
    }

    /** {bizType小写}/{yyyyMMdd}/{uuid}{原始扩展名}。 */
    private String buildObjectKey(FileBizType type, String fileName) {
        String extension = "";
        int dot = fileName.lastIndexOf('.');
        if (dot >= 0 && dot < fileName.length() - 1) {
            extension = fileName.substring(dot).toLowerCase(Locale.ROOT);
        }
        return "%s/%s/%s%s".formatted(type.name().toLowerCase(Locale.ROOT),
                LocalDate.now().format(DAY), UUID.randomUUID(), extension);
    }
}
