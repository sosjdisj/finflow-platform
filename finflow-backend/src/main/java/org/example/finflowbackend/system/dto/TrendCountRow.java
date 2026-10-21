package org.example.finflowbackend.system.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/**
 * 按日成功/总数行（支付成功率趋势用）。
 */
@Getter
@Setter
public class TrendCountRow {

    private LocalDate statDate;

    /** 当日创建总单数。 */
    private long total;

    /** 当日终态成功单数。 */
    private long success;
}
