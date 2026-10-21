package org.example.finflowbackend.reconciliation;

import org.example.finflowbackend.common.exception.BusinessException;
import org.example.finflowbackend.reconciliation.entity.ReconciliationBillItem;
import org.example.finflowbackend.reconciliation.service.BillCsvParser;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 渠道账单 CSV 解析单测：正常行、表头校验、金额/日期/列数非法整单拒绝、空行跳过。
 */
class BillCsvParserTest {

    private final BillCsvParser parser = new BillCsvParser();
    private static final String HEADER = "channel_order_no,merchant_order_no,amount,status,bill_date";

    @Test
    void parsesRowsAndSkipsBlankLines() {
        String csv = HEADER + "\n"
                + "CH2026001,MO2026001,10000,SUCCESS,2026-10-17\n"
                + "\n"
                + "CH2026002,MO2026002,9900,REFUNDED,2026-10-17\n";
        List<ReconciliationBillItem> items = parser.parse(new StringReader(csv), 1L);

        assertEquals(2, items.size());
        ReconciliationBillItem first = items.getFirst();
        assertEquals(1, first.getRowNo());
        assertEquals("CH2026001", first.getChannelOrderNo());
        assertEquals("MO2026001", first.getMerchantOrderNo());
        assertEquals(10000L, first.getAmount());
        assertEquals("SUCCESS", first.getBillStatus());
        assertEquals(2, items.get(1).getRowNo());
    }

    @Test
    void rejectsWrongHeader() {
        BusinessException error = assertThrows(BusinessException.class,
                () -> parser.parse(new StringReader("a,b,c,d,e\n"), 1L));
        assertEquals(ReconciliationErrorCode.BILL_FILE_INVALID, error.getErrorCode());
    }

    @Test
    void rejectsWrongColumnCount() {
        String csv = HEADER + "\nCH2026001,MO2026001,10000,SUCCESS\n";
        BusinessException error = assertThrows(BusinessException.class,
                () -> parser.parse(new StringReader(csv), 1L));
        assertEquals(ReconciliationErrorCode.BILL_ROW_INVALID, error.getErrorCode());
    }

    @Test
    void rejectsNonIntegralAmount() {
        String csv = HEADER + "\nCH2026001,MO2026001,100.50,SUCCESS,2026-10-17\n";
        BusinessException error = assertThrows(BusinessException.class,
                () -> parser.parse(new StringReader(csv), 1L));
        assertEquals(ReconciliationErrorCode.BILL_ROW_INVALID, error.getErrorCode());
    }

    @Test
    void rejectsNonPositiveAmount() {
        String csv = HEADER + "\nCH2026001,MO2026001,0,SUCCESS,2026-10-17\n";
        BusinessException error = assertThrows(BusinessException.class,
                () -> parser.parse(new StringReader(csv), 1L));
        assertEquals(ReconciliationErrorCode.BILL_ROW_INVALID, error.getErrorCode());
    }

    @Test
    void rejectsBadDate() {
        String csv = HEADER + "\nCH2026001,MO2026001,10000,SUCCESS,2026/10/17\n";
        BusinessException error = assertThrows(BusinessException.class,
                () -> parser.parse(new StringReader(csv), 1L));
        assertEquals(ReconciliationErrorCode.BILL_ROW_INVALID, error.getErrorCode());
    }

    @Test
    void rejectsBlankField() {
        String csv = HEADER + "\nCH2026001,,10000,SUCCESS,2026-10-17\n";
        BusinessException error = assertThrows(BusinessException.class,
                () -> parser.parse(new StringReader(csv), 1L));
        assertEquals(ReconciliationErrorCode.BILL_ROW_INVALID, error.getErrorCode());
    }
}
