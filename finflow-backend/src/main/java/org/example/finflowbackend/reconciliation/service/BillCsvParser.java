package org.example.finflowbackend.reconciliation.service;

import org.example.finflowbackend.common.exception.BusinessException;
import org.example.finflowbackend.reconciliation.ReconciliationErrorCode;
import org.example.finflowbackend.reconciliation.entity.ReconciliationBillItem;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * 渠道账单 CSV 解析器（S27 模拟格式，纯解析无副作用，便于单测）。
 * 格式：表头 channel_order_no,merchant_order_no,amount,status,bill_date；
 * amount 单位分（整数），status 为渠道侧状态（SUCCESS/REFUNDED/FAILED）。
 * 任一行非法整单拒绝（BILL_ROW_INVALID，带行号）——账单要求数据完整，宁拒不脏。
 */
@Component
public class BillCsvParser {

    public static final String HEADER = "channel_order_no,merchant_order_no,amount,status,bill_date";

    public List<ReconciliationBillItem> parse(Path csvFile, long taskId) {
        try (BufferedReader reader = Files.newBufferedReader(csvFile, StandardCharsets.UTF_8)) {
            return parse(reader, taskId);
        } catch (IOException e) {
            throw new BusinessException(ReconciliationErrorCode.BILL_FILE_INVALID, "账单文件读取失败");
        }
    }

    public List<ReconciliationBillItem> parse(Reader reader, long taskId) {
        List<ReconciliationBillItem> items = new ArrayList<>();
        int rowNo = 0;
        try (BufferedReader br = new BufferedReader(reader)) {
            String line = br.readLine();
            if (line == null || !line.strip().equals(HEADER)) {
                throw new BusinessException(ReconciliationErrorCode.BILL_FILE_INVALID,
                        "表头必须为: " + HEADER);
            }
            while ((line = br.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                items.add(parseRow(line, ++rowNo, taskId));
            }
        } catch (IOException e) {
            throw new BusinessException(ReconciliationErrorCode.BILL_FILE_INVALID, "账单文件读取失败");
        }
        return items;
    }

    private ReconciliationBillItem parseRow(String line, int rowNo, long taskId) {
        String[] cells = line.split(",", -1);
        if (cells.length != 5) {
            throw new BusinessException(ReconciliationErrorCode.BILL_ROW_INVALID, "第 " + rowNo + " 行列数非法");
        }
        long amount;
        try {
            amount = Long.parseLong(cells[2].strip());
        } catch (NumberFormatException e) {
            throw new BusinessException(ReconciliationErrorCode.BILL_ROW_INVALID,
                    "第 " + rowNo + " 行金额必须为分（整数）");
        }
        if (amount <= 0) {
            throw new BusinessException(ReconciliationErrorCode.BILL_ROW_INVALID,
                    "第 " + rowNo + " 行金额必须为正数");
        }
        String billStatus = cells[3].strip();
        LocalDate billDate;
        try {
            billDate = LocalDate.parse(cells[4].strip());
        } catch (DateTimeParseException e) {
            throw new BusinessException(ReconciliationErrorCode.BILL_ROW_INVALID,
                    "第 " + rowNo + " 行账单日期必须为 yyyy-MM-dd");
        }
        if (cells[0].isBlank() || cells[1].isBlank() || billStatus.isBlank()) {
            throw new BusinessException(ReconciliationErrorCode.BILL_ROW_INVALID,
                    "第 " + rowNo + " 行存在空字段");
        }
        ReconciliationBillItem item = new ReconciliationBillItem();
        item.setTaskId(taskId);
        item.setRowNo(rowNo);
        item.setChannelOrderNo(cells[0].strip());
        item.setMerchantOrderNo(cells[1].strip());
        item.setAmount(amount);
        item.setBillStatus(billStatus);
        item.setBillDate(billDate);
        return item;
    }
}
