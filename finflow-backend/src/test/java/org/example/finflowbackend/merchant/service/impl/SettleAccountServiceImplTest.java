package org.example.finflowbackend.merchant.service.impl;

import org.example.finflowbackend.common.exception.BusinessException;
import org.example.finflowbackend.merchant.MerchantErrorCode;
import org.example.finflowbackend.merchant.dto.SettleAccountCreateRequest;
import org.example.finflowbackend.merchant.entity.MerchantSettleAccount;
import org.example.finflowbackend.merchant.mapper.MerchantSettleAccountMapper;
import org.example.finflowbackend.merchant.service.MerchantService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 结算账户单测：脱敏、首个账户自动默认、设默认清其它、银行卡开户行必填、跨商户隔离。
 */
class SettleAccountServiceImplTest {

    private final MerchantSettleAccountMapper accountMapper = mock(MerchantSettleAccountMapper.class);
    private final MerchantService merchantService = mock(MerchantService.class);
    private final SettleAccountServiceImpl service =
            new SettleAccountServiceImpl(accountMapper, merchantService);

    @Test
    void createMasksAccountNoInResponse() {
        when(accountMapper.selectCount(any())).thenReturn(1L);

        var response = service.create(1L, request("BANK_CARD"));

        assertTrue(response.accountNoMasked().endsWith("8888"));
        assertTrue(response.accountNoMasked().startsWith("****"));
    }

    @Test
    void createFirstAccountAsDefault() {
        when(accountMapper.selectCount(any())).thenReturn(0L);

        var response = service.create(1L, request("ALIPAY"));

        assertEquals(1, response.isDefault());
    }

    @Test
    void createBankCardRequiresBankName() {
        when(accountMapper.selectCount(any())).thenReturn(0L);
        SettleAccountCreateRequest noBank = new SettleAccountCreateRequest("BANK_CARD", "张三", "6222020200112233445", null);

        BusinessException error = assertThrows(BusinessException.class, () -> service.create(1L, noBank));

        assertEquals(MerchantErrorCode.BANK_NAME_REQUIRED, error.getErrorCode());
    }

    @Test
    void markDefaultClearsOthersInSameTransaction() {
        when(accountMapper.selectOne(any())).thenReturn(account(5L, 1L, 0));

        var response = service.markDefault(1L, 5L);

        assertEquals(1, response.isDefault());
        // 两次 update（清全部默认 + 设目标），实际落库值由 HTTP 验收覆盖
        verify(accountMapper, times(2)).update(isNull(), any());
    }

    @Test
    void deleteRejectsAccountOfOtherMerchant() {
        when(accountMapper.selectOne(any())).thenReturn(null);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.delete(2L, 5L));

        assertEquals(MerchantErrorCode.SETTLE_ACCOUNT_NOT_FOUND, error.getErrorCode());
    }

    @Test
    void listSortsDefaultFirst() {
        when(accountMapper.selectList(any())).thenReturn(List.of(account(5L, 1L, 1), account(6L, 1L, 0)));

        var result = service.list(1L);

        assertEquals(2, result.size());
        assertEquals(5L, result.get(0).id());
    }

    private SettleAccountCreateRequest request(String type) {
        String bank = "BANK_CARD".equals(type) ? "招商银行" : null;
        return new SettleAccountCreateRequest(type, "张三", "62220202001122338888", bank);
    }

    private MerchantSettleAccount account(Long id, Long merchantId, int isDefault) {
        MerchantSettleAccount account = new MerchantSettleAccount();
        account.setId(id);
        account.setMerchantId(merchantId);
        account.setAccountType("BANK_CARD");
        account.setAccountName("张三");
        account.setAccountNo("62220202001122338888");
        account.setBankName("招商银行");
        account.setIsDefault(isDefault);
        return account;
    }
}
