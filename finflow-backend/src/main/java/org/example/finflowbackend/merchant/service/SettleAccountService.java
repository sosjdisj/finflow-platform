package org.example.finflowbackend.merchant.service;

import org.example.finflowbackend.merchant.dto.SettleAccountCreateRequest;
import org.example.finflowbackend.merchant.dto.SettleAccountResponse;

import java.util.List;

/**
 * 商户结算账户：信息管理（新增/列表/删除/设默认），不动真实资金。
 */
public interface SettleAccountService {

    SettleAccountResponse create(Long merchantId, SettleAccountCreateRequest request);

    List<SettleAccountResponse> list(Long merchantId);

    void delete(Long merchantId, Long accountId);

    SettleAccountResponse markDefault(Long merchantId, Long accountId);
}
