package org.example.finflowbackend.reconciliation.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.example.finflowbackend.common.api.PageResponse;
import org.example.finflowbackend.common.exception.BusinessException;
import org.example.finflowbackend.payment.service.ChannelConfigService;
import org.example.finflowbackend.reconciliation.ReconciliationErrorCode;
import org.example.finflowbackend.reconciliation.ReconciliationStatus;
import org.example.finflowbackend.reconciliation.entity.ReconciliationTask;
import org.example.finflowbackend.reconciliation.mapper.ReconciliationTaskMapper;
import org.example.finflowbackend.system.file.FileBizType;
import org.example.finflowbackend.system.file.FileObjectResponse;
import org.example.finflowbackend.system.file.FileObjectService;
import org.example.finflowbackend.system.file.FileStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.LocalDate;

/**
 * 对账任务上传编排（S27 输入侧，S38 存储切 MinIO）。
 * 流程：校验渠道与文件 → 同渠道同日查重（uk 兜底）→ 文件中心存 MinIO 并登记元数据 → 建任务 PENDING
 * → 委托 BillStore 事务内解析入库推 COMPLETED；解析失败整体回滚后推 FAILED 记原因并原样抛出。
 * 本类方法无事务：对象存储 IO 在事务外，事务边界在 BillStore 与单条 update 内。
 */
@Service
public class ReconciliationTaskService {

    private static final Logger log = LoggerFactory.getLogger(ReconciliationTaskService.class);
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int RETRY_TIMES = 3;

    private final ReconciliationTaskMapper taskMapper;
    private final ReconciliationBillStore billStore;
    private final ChannelConfigService channelConfigService;
    private final FileObjectService fileObjectService;
    private final FileStorage fileStorage;

    public ReconciliationTaskService(ReconciliationTaskMapper taskMapper,
                                     ReconciliationBillStore billStore,
                                     ChannelConfigService channelConfigService,
                                     FileObjectService fileObjectService,
                                     FileStorage fileStorage) {
        this.taskMapper = taskMapper;
        this.billStore = billStore;
        this.channelConfigService = channelConfigService;
        this.fileObjectService = fileObjectService;
        this.fileStorage = fileStorage;
    }

    /** 上传渠道账单并同步解析；返回带解析统计的任务。 */
    public ReconciliationTask upload(String channelCode, LocalDate billDate, MultipartFile file,
                                     String operator, long uploaderId) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ReconciliationErrorCode.BILL_FILE_INVALID, "账单文件为空");
        }
        String fileName = file.getOriginalFilename();
        if (fileName == null || !fileName.toLowerCase().endsWith(".csv")) {
            throw new BusinessException(ReconciliationErrorCode.BILL_FILE_INVALID, "仅支持 CSV 文件");
        }
        if (!channelConfigService.existsEnabled(channelCode)) {
            throw new BusinessException(ReconciliationErrorCode.CHANNEL_NOT_FOUND);
        }
        Long exists = taskMapper.selectCount(new LambdaQueryWrapper<ReconciliationTask>()
                .eq(ReconciliationTask::getChannelCode, channelCode)
                .eq(ReconciliationTask::getBillDate, billDate));
        if (exists != null && exists > 0) {
            throw new BusinessException(ReconciliationErrorCode.BILL_UPLOADED);
        }

        ReconciliationTask task = new ReconciliationTask();
        task.setTaskNo(nextTaskNo(billDate));
        task.setChannelCode(channelCode);
        task.setBillDate(billDate);
        task.setFileName(fileName);
        // 文件中心（S38）：对象入 MinIO + 登记 file_object 元数据；biz_id 不传（靠 task.file_path 反向关联）
        FileObjectResponse stored = fileObjectService.upload(file, FileBizType.RECONCILIATION.name(), null, uploaderId);
        task.setFilePath(stored.objectKey());
        task.setStatus(ReconciliationStatus.PENDING.name());
        task.setTotalRows(0);
        task.setValidRows(0);
        task.setUploadedBy(operator);
        try {
            taskMapper.insert(task);
        } catch (DuplicateKeyException e) {
            // 并发上传同渠道同日：uk 兜底
            throw new BusinessException(ReconciliationErrorCode.BILL_UPLOADED);
        }

        try {
            billStore.parseAndStore(task, readerOf(task.getFilePath()));
        } catch (Exception e) {
            markFailed(task, e.getMessage());
            throw e;
        }
        return task;
    }

    /** 从对象存储读账单为字符流（UTF-8）；调用方负责关闭。 */
    private Reader readerOf(String objectKey) {
        return new InputStreamReader(fileStorage.get(objectKey), StandardCharsets.UTF_8);
    }

    /** 解析失败标 FAILED（独立单条更新）；标记失败不吞原异常，仅告警。 */
    private void markFailed(ReconciliationTask task, String reason) {
        try {
            ReconciliationStatus.MACHINE.checkOrThrow(
                    ReconciliationStatus.valueOf(task.getStatus()), ReconciliationStatus.FAILED,
                    ReconciliationErrorCode.ILLEGAL_STATUS_TRANSITION);
            task.setStatus(ReconciliationStatus.FAILED.name());
            task.setErrorMessage(truncate(reason));
            taskMapper.updateById(task);
        } catch (Exception ex) {
            log.warn("[对账上传] 标记 FAILED 未成功: taskNo={}", task.getTaskNo(), ex);
        }
    }

    private String nextTaskNo(LocalDate billDate) {
        String datePart = billDate.format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE);
        for (int i = 0; i < RETRY_TIMES; i++) {
            String taskNo = "R" + datePart + "%06d".formatted(RANDOM.nextInt(1_000_000));
            if (taskMapper.selectCount(new LambdaQueryWrapper<ReconciliationTask>()
                    .eq(ReconciliationTask::getTaskNo, taskNo)) == 0) {
                return taskNo;
            }
        }
        throw new BusinessException(ReconciliationErrorCode.BILL_FILE_INVALID, "任务号生成失败，请重试");
    }

    private String truncate(String reason) {
        if (reason == null) {
            return "解析失败";
        }
        return reason.length() <= 500 ? reason : reason.substring(0, 500);
    }
}
