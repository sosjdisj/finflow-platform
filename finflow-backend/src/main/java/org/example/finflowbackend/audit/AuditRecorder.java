package org.example.finflowbackend.audit;

import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * 审计快照提交器（S35）：业务 Service 在变更处调用 {@link #submit(Object, Object)} 提交改前/改后快照，
 * 提交时立即序列化（防止后续原地修改污染 before 值），由 {@code AuditAspect} 统一取走并落库。
 * ThreadLocal 挂在切面清理，未匹配切面的调用不会留下脏数据（下次提交会覆盖）。
 */
@Component
public class AuditRecorder {

    private final ThreadLocal<Snapshots> holder = new ThreadLocal<>();
    private final ObjectMapper objectMapper;

    public AuditRecorder(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** 提交改前/改后快照（传对象/Map/记录均可，立即序列化为 JSON；null 表示无快照）。 */
    public void submit(Object before, Object after) {
        holder.set(new Snapshots(toJson(before), toJson(after)));
    }

    /** 取走当前线程的快照（取后清理），无提交时字段为 null。 */
    public Snapshots drain() {
        Snapshots snapshots = holder.get();
        holder.remove();
        return snapshots;
    }

    /** 清理当前线程的快照（切面 finally 兜底，防止线程复用串数据）。 */
    public void clear() {
        holder.remove();
    }

    private String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (RuntimeException e) {
            // 序列化失败降级为 toString，审计记录不能因为快照丢不了值而缺失
            return String.valueOf(value);
        }
    }

    /**
     * 快照载体：before/after 均为 JSON 串。
     */
    public record Snapshots(String before, String after) {
    }
}
