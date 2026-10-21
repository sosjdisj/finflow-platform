package org.example.finflowbackend.reconciliation.service;

import org.example.finflowbackend.reconciliation.ReconciliationErrorCode;
import org.example.finflowbackend.reconciliation.ReconciliationStatus;
import org.example.finflowbackend.reconciliation.entity.ReconciliationBillItem;
import org.example.finflowbackend.reconciliation.entity.ReconciliationTask;
import org.example.finflowbackend.reconciliation.mapper.ReconciliationBillItemMapper;
import org.example.finflowbackend.reconciliation.mapper.ReconciliationTaskMapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.Reader;
import java.util.List;

/**
 * 账单解析入库事务组件（S27；S38 入参从本地文件路径改为字符流）。
 * 单事务：推 PARSING → 解析 CSV → 暂存行逐条入库 → 更新统计推 COMPLETED——
 * 行数据与任务统计要么一致落库、要么整体回滚（铁律 5：一致性与状态推进同事务）。
 * 独立成 Bean：上传编排不得同类调用本方法，否则 @Transactional 不生效（S16/S24 踩坑）。
 * 解析失败整体回滚（暂存表不残留半单），由编排层另推 FAILED。
 */
@Component
public class ReconciliationBillStore {

    private final ReconciliationTaskMapper taskMapper;
    private final ReconciliationBillItemMapper itemMapper;
    private final BillCsvParser parser;

    public ReconciliationBillStore(ReconciliationTaskMapper taskMapper,
                                   ReconciliationBillItemMapper itemMapper,
                                   BillCsvParser parser) {
        this.taskMapper = taskMapper;
        this.itemMapper = itemMapper;
        this.parser = parser;
    }

    /** 解析入库并完成任务；行数即统计口径（表头与空行不计）。reader 由编排层打开与关闭。 */
    @Transactional
    public void parseAndStore(ReconciliationTask task, Reader reader) {
        transition(task, ReconciliationStatus.PARSING);
        List<ReconciliationBillItem> items = parser.parse(reader, task.getId());
        for (ReconciliationBillItem item : items) {
            itemMapper.insert(item);
        }
        task.setTotalRows(items.size());
        task.setValidRows(items.size());
        transition(task, ReconciliationStatus.COMPLETED);
    }

    /** 状态机集中校验后推进（本步无留痕表，转换校验仍走统一状态机，铁律 2）。 */
    private void transition(ReconciliationTask task, ReconciliationStatus to) {
        ReconciliationStatus.MACHINE.checkOrThrow(ReconciliationStatus.valueOf(task.getStatus()), to,
                ReconciliationErrorCode.ILLEGAL_STATUS_TRANSITION);
        task.setStatus(to.name());
        taskMapper.updateById(task);
    }
}
