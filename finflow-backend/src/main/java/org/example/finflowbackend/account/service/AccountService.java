package org.example.finflowbackend.account.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.example.finflowbackend.account.AccountErrorCode;
import org.example.finflowbackend.account.AccountOwnerType;
import org.example.finflowbackend.account.dto.AccountResponse;
import org.example.finflowbackend.account.entity.Account;
import org.example.finflowbackend.account.mapper.AccountMapper;
import org.example.finflowbackend.common.api.PageResponse;
import org.example.finflowbackend.common.exception.BusinessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 余额操作统一入口：入账 / 扣减 / 冻结 / 解冻，所有资金变更只能走这里。
 * 并发双保险：先 {@code select ... for update} 行锁读并校验余额，再经 MP @Version 乐观锁更新；
 * 行锁保证同账户操作串行，版本冲突即说明有更新绕过了本入口，直接失败暴露问题。
 */
@Service
public class AccountService {

    private final AccountMapper accountMapper;

    public AccountService(AccountMapper accountMapper) {
        this.accountMapper = accountMapper;
    }

    /** 幂等建户：同（主体类型, 主体ID, 币种）只建一次，并发建户由唯一索引兜底。 */
    @Transactional
    public Account getOrCreate(AccountOwnerType ownerType, Long ownerId, String currency) {
        Account existing = selectByOwner(ownerType, ownerId, currency);
        if (existing != null) {
            return existing;
        }
        Account account = new Account();
        account.setOwnerType(ownerType.name());
        account.setOwnerId(ownerId);
        account.setCurrency(currency);
        account.setAvailableBalance(0L);
        account.setFrozenBalance(0L);
        account.setVersion(0);
        try {
            accountMapper.insert(account);
            return account;
        } catch (DuplicateKeyException e) {
            return selectByOwner(ownerType, ownerId, currency);
        }
    }

    /** 读取账户，不存在抛 ACCOUNT_NOT_FOUND。 */
    @Transactional(readOnly = true)
    public Account getAccount(Long accountId) {
        Account account = accountMapper.selectById(accountId);
        if (account == null) {
            throw new BusinessException(AccountErrorCode.ACCOUNT_NOT_FOUND);
        }
        return account;
    }

    /** 入账：可用余额增加。 */
    @Transactional
    public Account credit(Long accountId, long amount) {
        Account account = lockAndValidateAmount(accountId, amount);
        account.setAvailableBalance(account.getAvailableBalance() + amount);
        return update(account);
    }

    /** 扣减：可用余额减少，不足拒绝。 */
    @Transactional
    public Account debit(Long accountId, long amount) {
        Account account = lockAndValidateAmount(accountId, amount);
        if (account.getAvailableBalance() < amount) {
            throw new BusinessException(AccountErrorCode.ACCOUNT_INSUFFICIENT_BALANCE);
        }
        account.setAvailableBalance(account.getAvailableBalance() - amount);
        return update(account);
    }

    /** 冻结：可用 → 冻结，可用不足拒绝。 */
    @Transactional
    public Account freeze(Long accountId, long amount) {
        Account account = lockAndValidateAmount(accountId, amount);
        if (account.getAvailableBalance() < amount) {
            throw new BusinessException(AccountErrorCode.ACCOUNT_INSUFFICIENT_BALANCE);
        }
        account.setAvailableBalance(account.getAvailableBalance() - amount);
        account.setFrozenBalance(account.getFrozenBalance() + amount);
        return update(account);
    }

    /** 解冻：冻结 → 可用，冻结不足拒绝。 */
    @Transactional
    public Account unfreeze(Long accountId, long amount) {
        Account account = lockAndValidateAmount(accountId, amount);
        if (account.getFrozenBalance() < amount) {
            throw new BusinessException(AccountErrorCode.INSUFFICIENT_FROZEN);
        }
        account.setFrozenBalance(account.getFrozenBalance() - amount);
        account.setAvailableBalance(account.getAvailableBalance() + amount);
        return update(account);
    }

    /** 账户分页（管理端查询）：按主体类型 / 币种筛选。 */
    @Transactional(readOnly = true)
    public PageResponse<AccountResponse> page(long page, long size, String ownerType, String currency) {
        Page<Account> result = accountMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<Account>()
                        .eq(ownerType != null && !ownerType.isBlank(), Account::getOwnerType, ownerType)
                        .eq(currency != null && !currency.isBlank(), Account::getCurrency, currency)
                        .orderByDesc(Account::getId));
        return PageResponse.of(result.convert(AccountResponse::from));
    }

    private Account selectByOwner(AccountOwnerType ownerType, Long ownerId, String currency) {
        return accountMapper.selectOne(new LambdaQueryWrapper<Account>()
                .eq(Account::getOwnerType, ownerType.name())
                .eq(Account::getOwnerId, ownerId)
                .eq(Account::getCurrency, currency));
    }

    private Account lockAndValidateAmount(Long accountId, long amount) {
        if (amount <= 0) {
            throw new BusinessException(AccountErrorCode.AMOUNT_INVALID);
        }
        Account account = accountMapper.selectForUpdate(accountId);
        if (account == null) {
            throw new BusinessException(AccountErrorCode.ACCOUNT_NOT_FOUND);
        }
        return account;
    }

    /** 带 @Version 的更新；影响行数为 0 视为并发冲突。 */
    private Account update(Account account) {
        if (accountMapper.updateById(account) == 0) {
            throw new BusinessException(AccountErrorCode.ACCOUNT_CONCURRENT_MODIFY);
        }
        return account;
    }
}
