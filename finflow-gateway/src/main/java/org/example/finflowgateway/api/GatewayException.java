package org.example.finflowgateway.api;

/**
 * 网关拒绝请求的受控异常：只带对外错误码，由 envelope 过滤器统一写响应。
 * detail 为空时用错误码默认文案；不透出任何内部异常细节。
 */
public class GatewayException extends RuntimeException {

    private final GatewayErrorCode errorCode;

    public GatewayException(GatewayErrorCode errorCode) {
        this(errorCode, errorCode.getMessage());
    }

    public GatewayException(GatewayErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public GatewayErrorCode getErrorCode() {
        return errorCode;
    }
}
