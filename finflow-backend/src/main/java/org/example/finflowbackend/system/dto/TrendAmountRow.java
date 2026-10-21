package org.example.finflowbackend.system.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/**
 * 按日金额趋势行（交易额/退款趋势共用）。
 */
@Getter
@Setter
public class TrendAmountRow {

    private LocalDate statDate;

    /** 当日金额合计（分）。 */
    private long amount;
}
