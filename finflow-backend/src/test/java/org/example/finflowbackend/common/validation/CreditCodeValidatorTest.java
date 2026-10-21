package org.example.finflowbackend.common.validation;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 统一社会信用代码校验单测：真实代码通过、位数/字符集/校验码错误的拒绝，并验证注解已接上校验框架。
 */
class CreditCodeValidatorTest {

    /** 用于验证注解接线的样例载体。 */
    private record Sample(@CreditCode String code) {
    }

    private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void acceptsRealCodes() {
        assertTrue(CreditCodeValidator.matches("91350100M000100Y43"));
        assertTrue(CreditCodeValidator.matches("91330100799655058B"));
    }

    @Test
    void toleratesCaseAndSurroundingSpaces() {
        assertTrue(CreditCodeValidator.matches("  91350100m000100y43 "));
    }

    @Test
    void rejectsWrongLength() {
        assertFalse(CreditCodeValidator.matches("111"));
        assertFalse(CreditCodeValidator.matches("91350100M000100Y4"));
    }

    @Test
    void rejectsIllegalCharacters() {
        // I / O / S / V / Z 不在字符集内
        assertFalse(CreditCodeValidator.matches("91350100M000100Y4I"));
        assertFalse(CreditCodeValidator.matches("91350100M000100Z43"));
    }

    @Test
    void rejectsWrongCheckDigit() {
        assertFalse(CreditCodeValidator.matches("91350100M000100Y44"));
    }

    @Test
    void annotationRejectsInvalidCodeAndAllowsBlank() {
        assertEquals(1, violations("111").size());
        assertEquals(0, violations("91350100M000100Y43").size());
        // 空值交给 @NotBlank 处理，本注解不重复报错
        assertEquals(0, violations("").size());
        assertEquals(0, violations(null).size());
    }

    private static Set<ConstraintViolation<Sample>> violations(String code) {
        return VALIDATOR.validate(new Sample(code));
    }
}
