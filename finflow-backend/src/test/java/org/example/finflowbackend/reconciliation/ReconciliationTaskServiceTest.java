package org.example.finflowbackend.reconciliation;

import org.example.finflowbackend.common.exception.BusinessException;
import org.example.finflowbackend.payment.service.ChannelConfigService;
import org.example.finflowbackend.reconciliation.entity.ReconciliationTask;
import org.example.finflowbackend.reconciliation.mapper.ReconciliationTaskMapper;
import org.example.finflowbackend.reconciliation.service.ReconciliationBillStore;
import org.example.finflowbackend.reconciliation.service.ReconciliationTaskService;
import org.example.finflowbackend.system.file.FileBizType;
import org.example.finflowbackend.system.file.FileObjectResponse;
import org.example.finflowbackend.system.file.FileObjectService;
import org.example.finflowbackend.system.file.FileStorage;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayInputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 对账任务上传编排单测：输入校验、重复拒绝、成功委托、失败标 FAILED 且不吞原异常。
 * S38 起文件经文件中心入 MinIO：存储与元数据登记均 mock，解析正确性由 BillCsvParserTest 与集成测试覆盖。
 */
class ReconciliationTaskServiceTest {

    private static final String OBJECT_KEY = "reconciliation/20261017/test.csv";

    private final ReconciliationTaskMapper taskMapper = mock(ReconciliationTaskMapper.class);
    private final ReconciliationBillStore billStore = mock(ReconciliationBillStore.class);
    private final ChannelConfigService channelConfigService = mock(ChannelConfigService.class);
    private final FileObjectService fileObjectService = mock(FileObjectService.class);
    private final FileStorage fileStorage = mock(FileStorage.class);

    private ReconciliationTaskService newService() {
        return new ReconciliationTaskService(taskMapper, billStore, channelConfigService,
                fileObjectService, fileStorage);
    }

    private void stubFileCenter() {
        when(fileObjectService.upload(any(), eq(FileBizType.RECONCILIATION.name()), isNull(), eq(9L)))
                .thenReturn(new FileObjectResponse(1L, OBJECT_KEY, "bill.csv", "text/csv",
                        1L, "RECONCILIATION", null, 9L, null));
        when(fileStorage.get(OBJECT_KEY))
                .thenReturn(new ByteArrayInputStream("x".getBytes(StandardCharsets.UTF_8)));
    }

    private MockMultipartFile csv(String content) {
        return new MockMultipartFile("file", "bill.csv", "text/csv",
                content.getBytes());
    }

    @Test
    void uploadRejectsEmptyFile() {
        BusinessException error = assertThrows(BusinessException.class,
                () -> newService().upload("MOCK_WALLET", LocalDate.of(2026, 10, 17),
                        new MockMultipartFile("file", "bill.csv", "text/csv", new byte[0]), "admin", 9L));
        assertEquals(ReconciliationErrorCode.BILL_FILE_INVALID, error.getErrorCode());
        verify(fileObjectService, never()).upload(any(), anyString(), any(), anyLong());
    }

    @Test
    void uploadRejectsNonCsvFile() {
        MockMultipartFile file = new MockMultipartFile("file", "bill.xls", "text/plain", "x".getBytes());
        BusinessException error = assertThrows(BusinessException.class,
                () -> newService().upload("MOCK_WALLET", LocalDate.of(2026, 10, 17), file, "admin", 9L));
        assertEquals(ReconciliationErrorCode.BILL_FILE_INVALID, error.getErrorCode());
    }

    @Test
    void uploadRejectsDisabledChannel() {
        when(channelConfigService.existsEnabled("MOCK_WALLET")).thenReturn(false);
        BusinessException error = assertThrows(BusinessException.class,
                () -> newService().upload("MOCK_WALLET", LocalDate.of(2026, 10, 17),
                        csv("x"), "admin", 9L));
        assertEquals(ReconciliationErrorCode.CHANNEL_NOT_FOUND, error.getErrorCode());
    }

    @Test
    void uploadRejectsDuplicatedChannelBillDate() {
        when(channelConfigService.existsEnabled(anyString())).thenReturn(true);
        when(taskMapper.selectCount(any())).thenReturn(1L);
        BusinessException error = assertThrows(BusinessException.class,
                () -> newService().upload("MOCK_WALLET", LocalDate.of(2026, 10, 17),
                        csv("x"), "admin", 9L));
        assertEquals(ReconciliationErrorCode.BILL_UPLOADED, error.getErrorCode());
    }

    @Test
    void uploadCreatesPendingTaskThenDelegatesToStore() {
        when(channelConfigService.existsEnabled(anyString())).thenReturn(true);
        when(taskMapper.selectCount(any())).thenReturn(0L);
        stubFileCenter();

        ReconciliationTask result = newService().upload("MOCK_WALLET", LocalDate.of(2026, 10, 17),
                csv("x"), "admin", 9L);

        ArgumentCaptor<ReconciliationTask> captor = ArgumentCaptor.forClass(ReconciliationTask.class);
        verify(taskMapper).insert(captor.capture());
        ReconciliationTask inserted = captor.getValue();
        assertEquals("PENDING", inserted.getStatus());
        assertEquals("admin", inserted.getUploadedBy());
        // S38：file_path 存 objectKey，并已登记文件元数据
        assertEquals(OBJECT_KEY, inserted.getFilePath());
        verify(fileObjectService).upload(any(), eq(FileBizType.RECONCILIATION.name()), isNull(), eq(9L));
        // 任务号 R+日期+6位随机
        assertEquals("R20261017", result.getTaskNo().substring(0, 9));
        verify(billStore).parseAndStore(eq(inserted), any(Reader.class));
    }

    @Test
    void uploadMarksFailedAndRethrowsWhenParseFails() {
        when(channelConfigService.existsEnabled(anyString())).thenReturn(true);
        when(taskMapper.selectCount(any())).thenReturn(0L);
        stubFileCenter();
        doThrow(new BusinessException(ReconciliationErrorCode.BILL_ROW_INVALID, "第 1 行列数非法"))
                .when(billStore).parseAndStore(any(ReconciliationTask.class), any(Reader.class));

        BusinessException error = assertThrows(BusinessException.class,
                () -> newService().upload("MOCK_WALLET", LocalDate.of(2026, 10, 17), csv("x"), "admin", 9L));

        assertEquals(ReconciliationErrorCode.BILL_ROW_INVALID, error.getErrorCode());
        ArgumentCaptor<ReconciliationTask> captor = ArgumentCaptor.forClass(ReconciliationTask.class);
        verify(taskMapper).updateById(captor.capture());
        assertEquals("FAILED", captor.getValue().getStatus());
        assertEquals("第 1 行列数非法", captor.getValue().getErrorMessage());
    }

    @Test
    void uploadDoesNotTouchStoreWhenValidationFails() {
        when(taskMapper.selectCount(any())).thenReturn(1L);
        assertThrows(BusinessException.class,
                () -> newService().upload("MOCK_WALLET", LocalDate.of(2026, 10, 17), csv("x"), "admin", 9L));
        verify(taskMapper, never()).insert(any(ReconciliationTask.class));
        verify(billStore, never()).parseAndStore(any(ReconciliationTask.class), any(Reader.class));
    }

    @Test
    void uploadTaskNoUniqueAfterRetry() {
        when(channelConfigService.existsEnabled(anyString())).thenReturn(true);
        when(taskMapper.selectCount(any())).thenReturn(0L, 1L, 0L);
        stubFileCenter();

        ReconciliationTask result = newService().upload("MOCK_WALLET", LocalDate.of(2026, 10, 17),
                csv("x"), "admin", 9L);

        // 首次撞号重试后成功生成（R + 8 位日期 + 6 位随机 = 15 位）
        verify(taskMapper).insert(any(ReconciliationTask.class));
        assertEquals(15, result.getTaskNo().length());
    }
}
