package org.example.finflowbackend.risk.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 黑名单新增请求：用户（商户号）/ IP / 设备三维度。
 */
public record BlacklistCreateRequest(
        @NotBlank @Pattern(regexp = "USER|IP|DEVICE", message = "维度必须为 USER/IP/DEVICE") String dimension,
        @NotBlank @Size(max = 128) String value,
        @Size(max = 255) String reason) {
}
