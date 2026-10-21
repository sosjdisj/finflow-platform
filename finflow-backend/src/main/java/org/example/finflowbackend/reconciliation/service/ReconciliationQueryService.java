package org.example.finflowbackend.reconciliation.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.example.finflowbackend.common.api.PageResponse;
import org.example.finflowbackend.common.exception.BusinessException;
import org.example.finflowbackend.reconciliation.ReconciliationErrorCode;
import org.example.finflowbackend.reconciliation.entity.ReconciliationBillItem;
import org.example.finflowbackend.reconciliation.entity.ReconciliationRecord;
import org.example.finflowbackend.reconciliation.entity.ReconciliationTask;
import org.example.finflowbackend.reconciliation.mapper.ReconciliationBillItemMapper;
import org.example.finflowbackend.reconciliation.mapper.ReconciliationRecordMapper;
import org.example.finflowbackend.reconciliation.mapper.ReconciliationTaskMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * 对账任务查询：任务列表 / 详情 / 暂存行明细分页 / 匹配结果分页。供验收与前端页面使用。
 */
@Service
public class ReconciliationQueryService {

    private final ReconciliationTaskMapper taskMapper;
    private final ReconciliationBillItemMapper itemMapper;
    private final ReconciliationRecordMapper recordMapper;

    public ReconciliationQueryService(ReconciliationTaskMapper taskMapper,
                                      ReconciliationBillItemMapper itemMapper,
                                      ReconciliationRecordMapper recordMapper) {
        this.taskMapper = taskMapper;
        this.itemMapper = itemMapper;
        this.recordMapper = recordMapper;
    }

    /** 任务分页：按任务号 / 渠道 / 账单日 / 状态筛选。 */
    @Transactional(readOnly = true)
    public PageResponse<ReconciliationTask> page(long page, long size, String taskNo, String channelCode,
                                                 LocalDate billDate, String status) {
        Page<ReconciliationTask> result = taskMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<ReconciliationTask>()
                        .eq(taskNo != null && !taskNo.isBlank(), ReconciliationTask::getTaskNo, taskNo)
                        .eq(channelCode != null && !channelCode.isBlank(), ReconciliationTask::getChannelCode, channelCode)
                        .eq(billDate != null, ReconciliationTask::getBillDate, billDate)
                        .eq(status != null && !status.isBlank(), ReconciliationTask::getStatus, status)
                        .orderByDesc(ReconciliationTask::getId));
        return PageResponse.of(result);
    }

    @Transactional(readOnly = true)
    public ReconciliationTask getTask(Long id) {
        ReconciliationTask task = taskMapper.selectById(id);
        if (task == null) {
            throw new BusinessException(ReconciliationErrorCode.TASK_NOT_FOUND);
        }
        return task;
    }

    /** 暂存行明细分页（按行号升序）。 */
    @Transactional(readOnly = true)
    public PageResponse<ReconciliationBillItem> pageItems(Long taskId, long page, long size) {
        getTask(taskId);
        Page<ReconciliationBillItem> result = itemMapper.selectPage(new Page<>(page, size),
                Wrappers.<ReconciliationBillItem>query()
                        .eq("task_id", taskId).orderByAsc("row_no"));
        return PageResponse.of(result);
    }

    /** 匹配结果分页（对账结果页数据源）：按五类结果筛选。 */
    @Transactional(readOnly = true)
    public PageResponse<ReconciliationRecord> pageRecords(Long taskId, long page, long size, String result) {
        getTask(taskId);
        Page<ReconciliationRecord> records = recordMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<ReconciliationRecord>()
                        .eq(ReconciliationRecord::getTaskId, taskId)
                        .eq(result != null && !result.isBlank(), ReconciliationRecord::getResult, result)
                        .orderByAsc(ReconciliationRecord::getId));
        return PageResponse.of(records);
    }
}
