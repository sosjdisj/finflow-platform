package org.example.testsupport;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 测试专用：内存收集假渠道异步通知。放在 org.example.testsupport 包下（不在应用扫描根内）。
 */
public class NotificationRecorder {

    private final List<Received> received = new CopyOnWriteArrayList<>();

    public void clear() {
        received.clear();
    }

    public void add(Received notification) {
        received.add(notification);
    }

    public int size() {
        return received.size();
    }

    /** 取某笔平台订单收到的通知（按通知 body 上的 platformOrderNo 过滤，避免跨用例串扰）。 */
    public List<Received> forPlatformOrderNo(String platformOrderNo) {
        String needle = "\"platformOrderNo\":\"" + platformOrderNo + "\"";
        return received.stream().filter(r -> r.body().contains(needle)).toList();
    }

    /** 收到的通知：头 + 原始 body。 */
    public record Received(String channelCode, String timestamp, String signature, String body) {
    }
}
