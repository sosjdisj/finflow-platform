package org.example.fakechannel.web.dto;

/**
 * 渠道统一响应信封。刻意不复用 FinFlow 后端的 ApiResponse（渠道有自己的协议）。
 *
 * @param code    渠道业务码：成功 10000、业务失败 40004
 * @param message 说明
 * @param data    业务数据
 */
public record ChannelReply(String code, String message, Object data) {

    public static final String CODE_SUCCESS = "10000";
    public static final String CODE_BIZ_FAIL = "40004";
    public static final String CODE_SIGN_FAIL = "40001";

    public static ChannelReply ok(Object data) {
        return new ChannelReply(CODE_SUCCESS, "成功", data);
    }

    public static ChannelReply bizFail(String message) {
        return new ChannelReply(CODE_BIZ_FAIL, message, null);
    }
}
