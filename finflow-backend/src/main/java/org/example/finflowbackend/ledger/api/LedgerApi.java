package org.example.finflowbackend.ledger.api;

/**
 * ledger 模块对外能力（M03 facade）：入账 / 出账 / 调账，余额变更与流水写入在同一事务内。
 * 其余模块只能经本接口动账，不得直接读 ledger 表。
 */
public interface LedgerApi {

    /** 入账 + 流水（同事务）。 */
    LedgerEntry credit(Long accountId, String bizType, String bizNo, long amount, String source);

    /** 入账 + 流水，remark 随流水留痕。 */
    LedgerEntry credit(Long accountId, String bizType, String bizNo, long amount, String source, String remark);

    /** 出账 + 流水（同事务），amount 为正、流水记负数。 */
    LedgerEntry debit(Long accountId, String bizType, String bizNo, long amount, String source, String remark);

    /** 人工调账：amount 带符号（正调增、负调减），必须走流水。 */
    LedgerEntry adjust(Long accountId, long amount, String remark);
}
