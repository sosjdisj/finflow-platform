package org.example.finflowbackend.system.file;

import org.example.finflowbackend.common.exception.BusinessException;
import org.example.finflowbackend.system.SystemErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 文件中心服务单测（S38）：校验、objectKey 规则、元数据落库、流委托。存储为 mock。 */
class FileObjectServiceTest {

    private final FileStorage storage = mock(FileStorage.class);
    private final FileObjectMapper fileObjectMapper = mock(FileObjectMapper.class);
    private final FileObjectServiceImpl service = new FileObjectServiceImpl(storage, fileObjectMapper,
            new StorageProperties("http://localhost:9000", "finflow", "finflow123", "finflow"));

    private final MockMultipartFile file = new MockMultipartFile("file", "证件.pdf", "application/pdf",
            "pdf-bytes".getBytes(StandardCharsets.UTF_8));

    @BeforeEach
    void setUp() {
        // 生产端 MP 自增主键 insert 后回填 id；mock 需模拟，否则 record 拆箱 NPE
        org.mockito.Mockito.doAnswer(inv -> {
            inv.getArgument(0, FileObject.class).setId(100L);
            return 1;
        }).when(fileObjectMapper).insert(any(FileObject.class));
        when(fileObjectMapper.selectById(1L)).thenReturn(row());
        when(fileObjectMapper.selectById(2L)).thenReturn(null);
    }

    @Test
    void uploadRejectsEmptyFile() {
        BusinessException error = assertThrows(BusinessException.class, () -> service.upload(
                new MockMultipartFile("file", "a.pdf", "application/pdf", new byte[0]),
                "MERCHANT_CERT", null, 9L));
        assertEquals(SystemErrorCode.FILE_EMPTY, error.getErrorCode());
    }

    @Test
    void uploadRejectsUnknownBizType() {
        BusinessException error = assertThrows(BusinessException.class,
                () -> service.upload(file, "NOT_A_TYPE", null, 9L));
        assertEquals(SystemErrorCode.FILE_TYPE_INVALID, error.getErrorCode());
    }

    /** 成功上传：对象入存储、元数据落库、objectKey = {bizType小写}/{yyyyMMdd}/{uuid}{扩展名}。 */
    @Test
    void uploadStoresObjectAndPersistsMeta() {
        FileObjectResponse response = service.upload(file, "MERCHANT_CERT", 7L, 9L);

        verify(storage).put(eq(response.objectKey()), any(), eq(file.getSize()), eq("application/pdf"));
        assertTrue(response.objectKey().matches("merchant_cert/\\d{8}/[0-9a-f-]{36}\\.pdf"),
                "objectKey 应符合规则: " + response.objectKey());

        ArgumentCaptor<FileObject> captor = ArgumentCaptor.forClass(FileObject.class);
        verify(fileObjectMapper).insert(captor.capture());
        assertEquals(response.objectKey(), captor.getValue().getObjectKey());
        assertEquals("证件.pdf", captor.getValue().getFileName());
        assertEquals("MERCHANT_CERT", captor.getValue().getBizType());
        assertEquals(7L, captor.getValue().getBizId().longValue());
        assertEquals(9L, captor.getValue().getUploaderId().longValue());
    }

    @Test
    void getMetaReturnsRow() {
        assertEquals("bill.csv", service.getMeta(1L).fileName());
    }

    @Test
    void getMetaRejectsMissingFile() {
        BusinessException error = assertThrows(BusinessException.class, () -> service.getMeta(2L));
        assertEquals(SystemErrorCode.FILE_NOT_FOUND, error.getErrorCode());
    }

    /** 下载流：校验元数据存在后委托存储，流内容原样返回。 */
    @Test
    void openStreamDelegatesToStorage() throws java.io.IOException {
        when(storage.get("reconciliation/20261017/test.csv"))
                .thenReturn(new ByteArrayInputStream("csv-content".getBytes(StandardCharsets.UTF_8)));

        var in = service.openStream(1L);
        assertEquals("csv-content", new String(in.readAllBytes(), StandardCharsets.UTF_8));
    }

    private FileObject row() {
        FileObject fileObject = new FileObject();
        fileObject.setId(1L);
        fileObject.setObjectKey("reconciliation/20261017/test.csv");
        fileObject.setBucket("finflow");
        fileObject.setFileName("bill.csv");
        fileObject.setContentType("text/csv");
        fileObject.setSizeBytes(11L);
        fileObject.setBizType("RECONCILIATION");
        fileObject.setBizId(null);
        fileObject.setUploaderId(0L);
        return fileObject;
    }
}
