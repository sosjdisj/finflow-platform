package org.example.finflowbackend.reconciliation;

import org.example.finflowbackend.common.api.PageResponse;
import org.example.finflowbackend.common.exception.BusinessException;
import org.example.finflowbackend.reconciliation.entity.ReconciliationBillItem;
import org.example.finflowbackend.reconciliation.entity.ReconciliationTask;
import org.example.finflowbackend.reconciliation.mapper.ReconciliationTaskMapper;
import org.example.finflowbackend.reconciliation.service.ReconciliationQueryService;
import org.example.finflowbackend.reconciliation.service.ReconciliationTaskService;
import org.example.finflowbackend.system.file.FileObject;
import org.example.finflowbackend.system.file.FileObjectMapper;
import org.example.finflowbackend.system.file.FileStorage;
import org.example.finflowbackend.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.security.SecureRandom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * S27 集成验收（报告：上传一份示例账单，解析结果正确）；S38 起文件走 MinIO：
 * 上传 3 行示例 CSV → 任务 COMPLETED、统计正确、暂存行内容正确；file_object 已登记；对象可读回原内容。
 * 唯一随机未来账单日期隔离历史运行残留；依赖本地 MySQL/Redis/Kafka/MinIO，S39 换 Testcontainers。
 */
class ReconciliationUploadIntegrationTest extends IntegrationTestBase {

    private static final SecureRandom RANDOM = new SecureRandom();

    /** 唯一随机未来账单日期：宽范围高熵偏移，避免连续两次全量运行撞 uk(channel_code, bill_date)。 */
    private LocalDate uniqFutureDate() {
        return LocalDate.now().plusDays(1 + RANDOM.nextInt(1000));
    }

    /** 示例账单（V24 定义的模拟格式）：3 行数据，金额为分。 */
    private static final String SAMPLE_CSV = """
            channel_order_no,merchant_order_no,amount,status,bill_date
            CHIT2026001,MOIT2026001,10000,SUCCESS,%s
            CHIT2026002,MOIT2026002,9900,REFUNDED,%s
            CHIT2026003,MOIT2026003,500,FAILED,%s
            """;

    @Autowired
    private ReconciliationTaskService taskService;
    @Autowired
    private ReconciliationQueryService queryService;
    @Autowired
    private ReconciliationTaskMapper taskMapper;
    @Autowired
    private FileObjectMapper fileObjectMapper;
    @Autowired
    private FileStorage fileStorage;

    @Test
    void uploadSampleBillAndParseCorrectly() {
        LocalDate billDate = uniqFutureDate();
        String csv = SAMPLE_CSV.formatted(billDate, billDate, billDate);
        MockMultipartFile file = new MockMultipartFile("file", "sample-bill.csv", "text/csv",
                csv.getBytes());

        ReconciliationTask task = taskService.upload("MOCK_WALLET", billDate, file, "it-admin", 0L);

        // 任务：解析完成，统计 = 3/3
        assertEquals("COMPLETED", task.getStatus());
        assertEquals(3, task.getTotalRows());
        assertEquals(3, task.getValidRows());
        assertEquals("MOCK_WALLET", task.getChannelCode());
        assertEquals(billDate, task.getBillDate());
        assertEquals("it-admin", task.getUploadedBy());
        // S38：file_path 是 objectKey（reconciliation/ 前缀），file_object 已登记，对象内容可读回
        assertTrue(task.getFilePath().startsWith("reconciliation/"), "file_path 应为 objectKey: " + task.getFilePath());
        FileObject meta = fileObjectMapper.selectOne(
                com.baomidou.mybatisplus.core.toolkit.Wrappers.<FileObject>query()
                        .eq("object_key", task.getFilePath()));
        assertEquals("RECONCILIATION", meta.getBizType());
        assertEquals(csv.getBytes().length, meta.getSizeBytes().longValue());
        try (var in = fileStorage.get(task.getFilePath())) {
            assertEquals(csv, new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new AssertionError("对象读回失败", e);
        }

        // 暂存行：逐行内容正确
        PageResponse<ReconciliationBillItem> items = queryService.pageItems(task.getId(), 1, 20);
        assertEquals(3, items.total());
        List<ReconciliationBillItem> rows = items.records();
        assertEquals("CHIT2026001", rows.get(0).getChannelOrderNo());
        assertEquals("MOIT2026001", rows.get(0).getMerchantOrderNo());
        assertEquals(10000L, rows.get(0).getAmount());
        assertEquals("SUCCESS", rows.get(0).getBillStatus());
        assertEquals(billDate, rows.get(0).getBillDate());
        assertEquals(2, rows.get(1).getRowNo());
        assertEquals("REFUNDED", rows.get(1).getBillStatus());
        assertEquals("FAILED", rows.get(2).getBillStatus());
    }

    @Test
    void duplicatedUploadIsRejected() {
        LocalDate billDate = uniqFutureDate();
        MockMultipartFile file = new MockMultipartFile("file", "bill.csv", "text/csv",
                SAMPLE_CSV.formatted(billDate, billDate, billDate).getBytes());

        taskService.upload("MOCK_WALLET", billDate, file, "it-admin", 0L);
        BusinessException error = assertThrows(BusinessException.class,
                () -> taskService.upload("MOCK_WALLET", billDate, file, "it-admin", 0L));
        assertEquals(ReconciliationErrorCode.BILL_UPLOADED, error.getErrorCode());
    }

    @Test
    void badRowRejectsWholeBillAndMarksTaskFailed() {
        // 随机日期可能撞历史运行残留（同渠道同日已建任务），先物理清理保证用例自隔离
        LocalDate billDate = uniqFutureDate();
        taskMapper.delete(com.baomidou.mybatisplus.core.toolkit.Wrappers.<ReconciliationTask>query()
                .eq("channel_code", "MOCK_WALLET").eq("bill_date", billDate));
        // 第 2 行金额非法 → 整单拒绝，任务 FAILED，暂存表无残留
        String badCsv = """
                channel_order_no,merchant_order_no,amount,status,bill_date
                CHBAD0001,MOBAD0001,10000,SUCCESS,%s
                CHBAD0002,MOBAD0002,abc,SUCCESS,%s
                """.formatted(billDate, billDate);
        MockMultipartFile file = new MockMultipartFile("file", "bad-bill.csv", "text/csv",
                badCsv.getBytes());

        BusinessException error = assertThrows(BusinessException.class,
                () -> taskService.upload("MOCK_WALLET", billDate, file, "it-admin", 0L));
        assertEquals(ReconciliationErrorCode.BILL_ROW_INVALID, error.getErrorCode());

        ReconciliationTask task = taskMapper.selectOne(
                com.baomidou.mybatisplus.core.toolkit.Wrappers.<ReconciliationTask>query()
                        .eq("channel_code", "MOCK_WALLET").eq("bill_date", billDate));
        assertEquals("FAILED", task.getStatus());
        Long itemCount = queryService.pageItems(task.getId(), 1, 20).total();
        assertEquals(0, itemCount);
    }
}
