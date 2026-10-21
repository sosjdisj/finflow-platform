package org.example.finflowbackend.merchant.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.example.finflowbackend.common.exception.BusinessException;
import org.example.finflowbackend.merchant.MerchantErrorCode;
import org.example.finflowbackend.merchant.dto.SettleAccountCreateRequest;
import org.example.finflowbackend.merchant.dto.SettleAccountResponse;
import org.example.finflowbackend.merchant.entity.MerchantSettleAccount;
import org.example.finflowbackend.merchant.mapper.MerchantSettleAccountMapper;
import org.example.finflowbackend.merchant.service.MerchantService;
import org.example.finflowbackend.merchant.service.SettleAccountService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 结算账户实现：仅信息管理。首个账户自动设默认；设默认同事务清其它默认。
 */
@Service
public class SettleAccountServiceImpl implements SettleAccountService {

    private final MerchantSettleAccountMapper accountMapper;
    private final MerchantService merchantService;

    public SettleAccountServiceImpl(MerchantSettleAccountMapper accountMapper, MerchantService merchantService) {
        this.accountMapper = accountMapper;
        this.merchantService = merchantService;
    }

    @Override
    @Transactional
    public SettleAccountResponse create(Long merchantId, SettleAccountCreateRequest request) {
        merchantService.getDetail(merchantId);
        requireBankNameForBankCard(request);
        Long count = accountMapper.selectCount(
                Wrappers.<MerchantSettleAccount>query().eq("merchant_id", merchantId));
        MerchantSettleAccount account = new MerchantSettleAccount();
        account.setMerchantId(merchantId);
        account.setAccountType(request.accountType());
        account.setAccountName(request.accountName());
        account.setAccountNo(request.accountNo());
        account.setBankName(request.bankName());
        account.setIsDefault(count == null || count == 0 ? 1 : 0);
        accountMapper.insert(account);
        return SettleAccountResponse.from(account);
    }

    @Override
    public List<SettleAccountResponse> list(Long merchantId) {
        merchantService.getDetail(merchantId);
        return accountMapper.selectList(Wrappers.<MerchantSettleAccount>query()
                        .eq("merchant_id", merchantId).orderByDesc("is_default").orderByDesc("id"))
                .stream().map(SettleAccountResponse::from).toList();
    }

    @Override
    @Transactional
    public void delete(Long merchantId, Long accountId) {
        requireAccount(merchantId, accountId);
        accountMapper.delete(Wrappers.<MerchantSettleAccount>query()
                .eq("id", accountId).eq("merchant_id", merchantId));
    }

    @Override
    @Transactional
    public SettleAccountResponse markDefault(Long merchantId, Long accountId) {
        MerchantSettleAccount account = requireAccount(merchantId, accountId);
        // 同事务：清该商户全部默认，再设目标为默认
        accountMapper.update(null, Wrappers.<MerchantSettleAccount>update()
                .eq("merchant_id", merchantId).set("is_default", 0));
        accountMapper.update(null, Wrappers.<MerchantSettleAccount>update()
                .eq("id", accountId).set("is_default", 1));
        account.setIsDefault(1);
        return SettleAccountResponse.from(account);
    }

    private MerchantSettleAccount requireAccount(Long merchantId, Long accountId) {
        MerchantSettleAccount account = accountMapper.selectOne(Wrappers.<MerchantSettleAccount>query()
                .eq("id", accountId).eq("merchant_id", merchantId));
        if (account == null) {
            throw new BusinessException(MerchantErrorCode.SETTLE_ACCOUNT_NOT_FOUND);
        }
        return account;
    }

    private void requireBankNameForBankCard(SettleAccountCreateRequest request) {
        if ("BANK_CARD".equals(request.accountType())
                && (request.bankName() == null || request.bankName().isBlank())) {
            throw new BusinessException(MerchantErrorCode.BANK_NAME_REQUIRED);
        }
    }
}
