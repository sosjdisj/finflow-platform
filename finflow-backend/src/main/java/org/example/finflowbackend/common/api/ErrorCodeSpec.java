package org.example.finflowbackend.common.api;

/**
 * 错误码契约：公共 {@link ErrorCode} 与各模块自定义错误码枚举都实现它。
 * 这样业务异常可以接受任意模块的错误码，各模块业务码不必堆进公共枚举。
 */
public interface ErrorCodeSpec {

    int getCode();

    String getMessage();

    int getHttpStatus();
}
