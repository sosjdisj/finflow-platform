package org.example.finflowbackend.common.statemachine;

import org.example.finflowbackend.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StateMachineTest {

    enum Color { RED, GREEN, BLUE }

    private final StateMachine<Color> machine = StateMachine.of(Map.of(
            Color.RED, Set.of(Color.GREEN),
            Color.GREEN, Set.of(Color.RED, Color.BLUE)
    ));

    @Test
    void allowsDeclaredTransitions() {
        assertTrue(machine.canTransfer(Color.RED, Color.GREEN));
        assertTrue(machine.canTransfer(Color.GREEN, Color.BLUE));
    }

    @Test
    void rejectsUndeclaredTransitions() {
        assertFalse(machine.canTransfer(Color.RED, Color.BLUE));
        assertFalse(machine.canTransfer(Color.BLUE, Color.RED));
    }

    @Test
    void checkOrThrowThrowsWithModuleErrorCode() {
        FakeErrorCode error = new FakeErrorCode();

        BusinessException ex = assertThrows(BusinessException.class,
                () -> machine.checkOrThrow(Color.RED, Color.BLUE, error));

        assertEquals(error, ex.getErrorCode());
    }

    @Test
    void checkOrThrowPassesLegalTransition() {
        machine.checkOrThrow(Color.GREEN, Color.RED, new FakeErrorCode());
    }

    /** 仅用于测试的模块错误码替身。 */
    private static class FakeErrorCode implements org.example.finflowbackend.common.api.ErrorCodeSpec {
        @Override
        public int getCode() {
            return 99001;
        }

        @Override
        public String getMessage() {
            return "非法状态转换";
        }

        @Override
        public int getHttpStatus() {
            return 409;
        }
    }
}
