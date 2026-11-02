package org.example.finflowbackend.clearing.api;

import java.time.LocalDate;
import java.util.List;

/**
 * 清分查询的对外能力（M03 facade）：结算模块按结算日读取已清分记录。
 * 清分单分页与详情等后台能力留在模块内部服务。
 */
public interface ClearingQueryApi {

    /** 某结算日状态为 CLEARED 的清分记录快照。 */
    List<ClearingOrderSnapshot> listClearedBySettleDate(LocalDate settleDate);
}
