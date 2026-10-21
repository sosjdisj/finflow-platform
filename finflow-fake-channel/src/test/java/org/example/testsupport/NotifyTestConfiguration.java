package org.example.testsupport;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * 测试配置：注册通知接收器与接收端点。
 */
@TestConfiguration
public class NotifyTestConfiguration {

    @Bean
    public NotificationRecorder notificationRecorder() {
        return new NotificationRecorder();
    }

    @Bean
    public TestNotifyController testNotifyController(NotificationRecorder recorder) {
        return new TestNotifyController(recorder);
    }
}
