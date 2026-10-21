package org.example.finflowbackend.auth.dto;

/** 图形验证码：Base64 编码的 PNG dataURL，前端直接塞进 <img src>。 */
public record CaptchaResponse(String captchaId, String imageBase64) {
}
