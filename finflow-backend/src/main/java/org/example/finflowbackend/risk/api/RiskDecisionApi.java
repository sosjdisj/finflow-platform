package org.example.finflowbackend.risk.api;

/**
 * 交易风控决策接口（M09）：payment 在建单之后、渠道受理之前只调用这一次，
 * 由实现方完成规则求值、留痕与超时降级，调用方不再自行判断规则。
 * M11 拆出风控服务后，本接口可直接作为 Feign 契约，请求 / 响应模型不变。
 */
public interface RiskDecisionApi {

    RiskDecisionResponse decide(RiskDecisionRequest request);
}
