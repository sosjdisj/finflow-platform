package org.example.finflowbackend.notification.service;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 占位符渲染器单测（S33）。
 */
class TemplateRendererTest {

    private final TemplateRenderer renderer = new TemplateRenderer();

    @Test
    void replacesPlaceholders() {
        String result = renderer.render("订单 {{platformOrderNo}} 已支付，金额 {{amount}} {{currency}}",
                Map.of("platformOrderNo", "PO1", "amount", "100.00", "currency", "CNY"));

        assertEquals("订单 PO1 已支付，金额 100.00 CNY", result);
    }

    @Test
    void keepsMissingPlaceholderAsIs() {
        // 缺失参数原样保留：便于发现模板与参数不匹配，而不是静默吞掉
        String result = renderer.render("您好 {{merchantName}}", Map.of());

        assertEquals("您好 {{merchantName}}", result);
    }

    @Test
    void templateWithoutPlaceholdersUnchanged() {
        assertEquals("纯文本", renderer.render("纯文本", Map.of("a", "b")));
    }
}
