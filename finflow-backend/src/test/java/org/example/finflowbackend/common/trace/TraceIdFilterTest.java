package org.example.finflowbackend.common.trace;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * M15：TraceIdFilter 与 Micrometer Tracing 共存规则——入站 X-Trace-Id 优先，
 * 其次沿用 tracing 上下文的 MDC，最后才生成；只清理自己写入的 MDC。
 */
class TraceIdFilterTest {

    private static final String TRACING_TRACE_ID = "4bf92f3577b34da6a3ce929d0e0e4736";

    private final TraceIdFilter filter = new TraceIdFilter();

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void usesTracingContextAndKeepsItsScope() throws Exception {
        MDC.put(TraceContext.KEY, TRACING_TRACE_ID);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/health");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> {
        });

        assertEquals(TRACING_TRACE_ID, response.getHeader(TraceIdFilter.HEADER));
        assertEquals(TRACING_TRACE_ID, MDC.get(TraceContext.KEY), "observation 过滤器建立的作用域不能被本过滤器清理");
    }

    @Test
    void incomingHeaderWinsAndRestoresTracingContext() throws Exception {
        MDC.put(TraceContext.KEY, "from-traceparent");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/health");
        request.addHeader(TraceIdFilter.HEADER, "from-nginx");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> {
        });

        assertEquals("from-nginx", response.getHeader(TraceIdFilter.HEADER));
        assertEquals("from-traceparent", MDC.get(TraceContext.KEY), "处理完应还原 tracing 上下文，不能污染同线程后续日志");
    }

    @Test
    void generatesTraceIdAndCleansMdcWhenNothingUpstream() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/health");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> {
        });

        String traceId = response.getHeader(TraceIdFilter.HEADER);
        assertTrue(traceId != null && traceId.matches("[0-9a-f]{32}"), "应生成 32 位十六进制: " + traceId);
        assertNull(MDC.get(TraceContext.KEY), "自造的 traceId 必须清理，避免线程复用串日志");
    }

    @Test
    void ignoresOversizedIncomingHeader() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/health");
        request.addHeader(TraceIdFilter.HEADER, "x".repeat(80));
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> {
        });

        String traceId = response.getHeader(TraceIdFilter.HEADER);
        assertTrue(traceId != null && traceId.matches("[0-9a-f]{32}"), "超长头不可信，应重新生成: " + traceId);
    }
}
