package org.example.finflowbackend.common.statemachine;

import org.example.finflowbackend.common.api.ErrorCodeSpec;
import org.example.finflowbackend.common.exception.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Set;

/**
 * 通用状态机：各模块用它声明「合法转换表」，状态判断集中于此，禁止在业务代码里散写 if-else。
 * 非法转换抛业务异常并记录 warn 日志（满足「非法转换抛异常并记录」）。
 * S08 商户、S12 支付订单等复用本组件。
 */
public final class StateMachine<S extends Enum<S>> {

    private static final Logger log = LoggerFactory.getLogger(StateMachine.class);

    private final Map<S, Set<S>> allowedTransitions;

    private StateMachine(Map<S, Set<S>> allowedTransitions) {
        this.allowedTransitions = Map.copyOf(allowedTransitions);
    }

    /** 以「from → 可去的 to 集合」构建状态机，构建后不可变。 */
    public static <S extends Enum<S>> StateMachine<S> of(Map<S, Set<S>> allowedTransitions) {
        return new StateMachine<>(allowedTransitions);
    }

    public boolean canTransfer(S from, S to) {
        return allowedTransitions.getOrDefault(from, Set.of()).contains(to);
    }

    /** 校验状态转换，非法时抛出携带模块错误码的业务异常。 */
    public void checkOrThrow(S from, S to, ErrorCodeSpec illegalTransitionError) {
        if (from == to || !canTransfer(from, to)) {
            log.warn("非法状态转换被拒绝: {} -> {}", from, to);
            throw new BusinessException(illegalTransitionError,
                    "非法状态转换: " + from + " -> " + to);
        }
    }
}
