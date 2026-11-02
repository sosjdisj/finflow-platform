package org.example.finflowbackend.risk.config;

import io.micrometer.context.ContextExecutorService;
import io.micrometer.context.ContextSnapshotFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 风控决策线程池（M09）：决策调用放到独立有界线程池执行，超时由调用线程按预算等待。
 * 线程池饱和时直接拒绝（AbortPolicy），由 {@code RiskDecisionService} 按 on-timeout 策略降级，不阻塞支付线程。
 *
 * <p>M15：决策任务在池内线程执行，默认拿不到调用线程的 tracing 上下文，风控日志与拦截事件会脱离
 * 整条支付链路；这里用 Micrometer 的上下文快照包装线程池，把调用线程的 traceId / spanId 带进池内。</p>
 */
@Configuration
public class RiskDecisionConfig {

    private static final int THREADS = 2;
    private static final int QUEUE_CAPACITY = 32;
    private static final AtomicInteger SEQ = new AtomicInteger();

    @Bean(name = "riskDecisionExecutor", destroyMethod = "shutdown")
    public ExecutorService riskDecisionExecutor() {
        ThreadPoolExecutor pool = new ThreadPoolExecutor(THREADS, THREADS, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(QUEUE_CAPACITY),
                runnable -> {
                    Thread thread = new Thread(runnable, "risk-decision-" + SEQ.incrementAndGet());
                    thread.setDaemon(true);
                    return thread;
                },
                new ThreadPoolExecutor.AbortPolicy());
        return ContextExecutorService.wrap(pool, ContextSnapshotFactory.builder().build());
    }
}
