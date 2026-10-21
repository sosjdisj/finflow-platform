package org.example.testsupport;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 测试专用接收端点 {@code POST /test/notify}：把收到的通知存进 {@link NotificationRecorder}。
 * 通过 {@link NotifyTestConfiguration} 以 @Bean 注册（该类不在应用扫描根内，避免被误扫）。
 */
@RestController
public class TestNotifyController {

    private final NotificationRecorder recorder;

    public TestNotifyController(NotificationRecorder recorder) {
        this.recorder = recorder;
    }

    @PostMapping("/test/notify")
    public Map<String, Object> receive(@RequestBody String body,
                                       @RequestHeader(name = "X-Channel-Code", required = false) String channelCode,
                                       @RequestHeader(name = "X-Timestamp", required = false) String timestamp,
                                       @RequestHeader(name = "X-Signature", required = false) String signature) {
        recorder.add(new NotificationRecorder.Received(channelCode, timestamp, signature, body));
        return Map.of("code", "10000", "message", "received");
    }
}
