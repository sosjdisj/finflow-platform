package org.example.finflowbackend.common.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.Locale;

/**
 * 统一社会信用代码校验实现。
 * 校验规则：18 位、只含规定字符集（剔除易混淆的 I/O/S/V/Z）、末位为前 17 位按加权模 31 算出的校验码。
 */
public class CreditCodeValidator implements ConstraintValidator<CreditCode, String> {

    /** 代码字符集（GB 32100-2015 附表）。 */
    private static final String CHARSET = "0123456789ABCDEFGHJKLMNPQRTUWXY";

    /** 前 17 位的加权因子。 */
    private static final int[] WEIGHTS = {1, 3, 9, 27, 19, 26, 16, 17, 20, 29, 25, 13, 8, 24, 10, 30, 28};

    /** 校验模数。 */
    private static final int MODULUS = 31;

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        // 空值交由 @NotBlank 判断，这里不重复报错
        return value == null || value.isBlank() || matches(value.trim());
    }

    /** 判断单个统一社会信用代码是否合法（忽略大小写与首尾空格）。 */
    public static boolean matches(String code) {
        String value = code.trim().toUpperCase(Locale.ROOT);
        if (value.length() != WEIGHTS.length + 1) {
            return false;
        }
        int sum = 0;
        for (int i = 0; i < WEIGHTS.length; i++) {
            int index = CHARSET.indexOf(value.charAt(i));
            if (index < 0) {
                return false;
            }
            sum += index * WEIGHTS[i];
        }
        int expected = (MODULUS - sum % MODULUS) % MODULUS;
        return CHARSET.indexOf(value.charAt(WEIGHTS.length)) == expected;
    }
}
