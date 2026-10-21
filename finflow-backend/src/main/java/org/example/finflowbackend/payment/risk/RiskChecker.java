package org.example.finflowbackend.payment.risk;

/**
 * 交易风控接入口（payment 模块依赖的唯一风控抽象）：
 * 支付编排在建单后、渠道受理前调用，其余模块不感知风控实现。
 * 阶段 8（风控模块）提供真实实现替换默认的 {@link PassAllRiskChecker}。
 */
public interface RiskChecker {

    RiskCheckContext.Decision check(RiskCheckContext context);
}
