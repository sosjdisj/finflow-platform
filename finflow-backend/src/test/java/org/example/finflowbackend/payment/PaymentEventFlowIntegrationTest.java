package org.example.finflowbackend.payment;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.example.finflowbackend.payment.dto.PaymentOrderCreateRequest;
import org.example.finflowbackend.payment.dto.PaymentOrderResponse;
import org.example.finflowbackend.payment.event.PaymentTopics;
import org.example.finflowbackend.payment.service.PaymentProcessService;
import org.example.finflowbackend.support.IntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * S16 事件链路验收（依赖本地 MySQL、Redis 与 Kafka 容器，S39 换 Testcontainers）：
 * 支付全链路（建单→受理→回调成功）后，finflow.payment.created 与 finflow.payment.succeeded
 * 两个主题各发布一条领域事件，事件带唯一 eventId 供消费端幂等去重。
 */
class PaymentEventFlowIntegrationTest extends IntegrationTestBase {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PaymentProcessService paymentProcessService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private Long merchantId;

    @BeforeEach
    void setUp() {
        String suffix = Long.toHexString(System.nanoTime());
        String name = "事件验收商户-" + suffix;
        jdbcTemplate.update("""
                        INSERT INTO merchant (merchant_no, merchant_name, contact_name, contact_phone,
                                              business_license_no, settle_cycle, status)
                        VALUES (?, ?, '测试联系人', '13800000000', ?, 'T1', 'ACTIVE')
                        """,
                "MEV" + suffix, name, "91110000MA01EV" + suffix.substring(0, 5).toUpperCase());
        merchantId = jdbcTemplate.queryForObject(
                "SELECT id FROM merchant WHERE merchant_name = ?", Long.class, name);
    }

    @Test
    void paymentFlowPublishesCreatedAndSucceededEvents() throws Exception {
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(consumerProps())) {
            consumer.subscribe(List.of(PaymentTopics.TOPIC_CREATED, PaymentTopics.TOPIC_SUCCEEDED));
            // 订阅就位后再触发链路，避免漏收
            Thread.sleep(1000);

            PaymentOrderResponse order = paymentProcessService.createAndProcess(createOrder());
            long orderId = order.id();

            // it 库 topic 含历史轮次事件；一次收集全部（同一 consumer 二次 poll 会丢已拉记录），再按本订单匹配
            List<ConsumerRecord<String, String>> all = pollAll(consumer, 15);
            var created = all.stream()
                    .filter(r -> r.topic().equals(PaymentTopics.TOPIC_CREATED) && hasOrderId(r.value(), orderId))
                    .findFirst().orElseThrow(() -> new AssertionError("未收到本订单的 PaymentCreated 事件"));
            var succeeded = all.stream()
                    .filter(r -> r.topic().equals(PaymentTopics.TOPIC_SUCCEEDED) && hasOrderId(r.value(), orderId))
                    .findFirst().orElseThrow(() -> new AssertionError("未收到本订单的 PaymentSucceeded 事件"));

            assertEvent(created.value(), PaymentTopics.TYPE_CREATED, orderId);
            assertEvent(succeeded.value(), PaymentTopics.TYPE_SUCCEEDED, orderId);
            // 分区 key = orderId，同单事件有序
            assertEquals(String.valueOf(orderId), created.key());
        }
    }

    private Properties consumerProps() {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "it-event-" + UUID.randomUUID());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        return props;
    }

    private PaymentOrderCreateRequest createOrder() {
        return new PaymentOrderCreateRequest(merchantId, "EVT-" + System.nanoTime(), 10000L, null,
                "事件验收商品", null, "ALIPAY", null, null, null, null);
    }

    /** 轮询收集记录：数据静默 5 秒即返回（最长 15 秒），给异步回调链留足发布窗口。 */
    private List<ConsumerRecord<String, String>> pollAll(KafkaConsumer<String, String> consumer,
                                                         int timeoutSeconds)
            throws InterruptedException {
        List<ConsumerRecord<String, String>> all = new java.util.ArrayList<>();
        long deadline = System.currentTimeMillis() + timeoutSeconds * 1000L;
        long quietDeadline = System.currentTimeMillis() + 2000;
        while (System.currentTimeMillis() < deadline && System.currentTimeMillis() < quietDeadline) {
            int before = all.size();
            consumer.poll(Duration.ofMillis(500)).forEach(all::add);
            if (all.size() > before) {
                quietDeadline = System.currentTimeMillis() + 5000;
            }
        }
        return all;
    }

    /** 事件 payload 里的 orderId 是否为目标订单。 */
    private boolean hasOrderId(String json, long orderId) {
        try {
            var event = objectMapper.readValue(json, java.util.Map.class);
            var payload = (java.util.Map<?, ?>) event.get("payload");
            return payload != null && payload.get("orderId") != null
                    && ((Number) payload.get("orderId")).longValue() == orderId;
        } catch (Exception e) {
            return false;
        }
    }

    private void assertEvent(String json, String expectedType, Long orderId) {
        var event = objectMapper.readValue(json, java.util.Map.class);
        assertEquals(expectedType, event.get("eventType"));
        assertNotNull(event.get("eventId"), "事件必须带唯一 eventId 供消费幂等");
        var payload = (java.util.Map<?, ?>) event.get("payload");
        assertEquals(orderId.intValue(), ((Number) payload.get("orderId")).intValue());
        assertTrue(payload.containsKey("platformOrderNo"));
    }
}
