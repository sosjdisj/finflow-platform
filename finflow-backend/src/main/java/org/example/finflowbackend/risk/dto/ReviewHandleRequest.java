package org.example.finflowbackend.risk.dto;

import jakarta.validation.constraints.Size;

/**
 * 风控审核处理请求：人工通过 / 拒绝时随单留痕的说明。
 */
public record ReviewHandleRequest(@Size(max = 255) String remark) {
}
