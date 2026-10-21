package org.example.finflowbackend.notification.service;

import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 极简占位符渲染器（S33）：把模板中的 {{key}} 替换为参数值。
 * 不引入模板引擎——占位符替换是这个场景的全部需求；缺失参数原样保留，便于发现模板与参数不匹配。
 */
@Component
public class TemplateRenderer {

    public String render(String template, Map<String, String> params) {
        String result = template;
        for (Map.Entry<String, String> entry : params.entrySet()) {
            result = result.replace("{{" + entry.getKey() + "}}", entry.getValue());
        }
        return result;
    }
}
