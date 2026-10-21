package org.example.finflowbackend.notification.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.example.finflowbackend.common.exception.BusinessException;
import org.example.finflowbackend.merchant.service.MerchantService;
import org.example.finflowbackend.notification.NotificationErrorCode;
import org.example.finflowbackend.notification.NotificationProperties;
import org.example.finflowbackend.notification.entity.Notification;
import org.example.finflowbackend.notification.entity.NotificationTemplate;
import org.example.finflowbackend.notification.mapper.NotificationMapper;
import org.example.finflowbackend.notification.mapper.NotificationTemplateMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.function.Supplier;

/**
 * 通知发送编排（S33）：查模板 → 渲染 → 落库（站内信即送达）→ 发邮件并记渠道状态。
 * 商户事件（支付/退款/风控拦截）收件人 MERCHANT，邮件发 contact_email；
 * 平台事件（对账异常）收件人 USER（receiver_id=0 平台公共信箱），邮件发 finflow.notification.platform-email。
 * 幂等：Redis 消费去重在前，notification.uk(event_id) 兜底在后。
 * 邮件失败/降级只记渠道状态，不影响站内信送达。
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    /** 平台公共信箱 receiver_id：通知为运营视角统一收件（无个人收件箱模型），0 表示平台广播。 */
    static final long PLATFORM_RECEIVER_ID = 0L;

    private final NotificationTemplateMapper templateMapper;
    private final NotificationMapper notificationMapper;
    private final TemplateRenderer renderer;
    private final MailChannel mailChannel;
    private final MerchantService merchantService;
    private final NotificationProperties properties;

    public NotificationService(NotificationTemplateMapper templateMapper,
                               NotificationMapper notificationMapper,
                               TemplateRenderer renderer,
                               MailChannel mailChannel,
                               MerchantService merchantService,
                               NotificationProperties properties) {
        this.templateMapper = templateMapper;
        this.notificationMapper = notificationMapper;
        this.renderer = renderer;
        this.mailChannel = mailChannel;
        this.merchantService = merchantService;
        this.properties = properties;
    }

    /** 发送结果（通知行 ID + 邮件渠道状态）。 */
    public record SendOutcome(Long notificationId, String emailStatus) {
    }

    /**
     * 发送商户通知。
     *
     * @param eventId    Kafka 事件 ID（幂等键）
     * @param merchantId 收件商户
     * @param params     渲染参数（已格式化为展示字符串）
     * @return 发送结果；事件已处理过（uk 命中）返回 null
     * @throws BusinessException 模板不存在或未启用（由消费方记日志后跳过）
     */
    public SendOutcome sendMerchantNotification(String templateCode, String eventId, Long merchantId,
                                                Map<String, String> params) {
        NotificationTemplate template = requireEnabledTemplate(templateCode);
        Notification notification = buildNotification(templateCode, eventId, "MERCHANT", merchantId,
                template, params);
        // 邮箱在落库成功后才解析：幂等命中（uk 冲突）时不做无谓的商户查询
        return persistAndEmail(notification, () -> merchantEmail(merchantId));
    }

    /**
     * 发送平台通知（对账异常等平台事件）：收件人 USER / 平台公共信箱，
     * 邮件发 finflow.notification.platform-email（未配置时 SKIPPED），不查商户。
     */
    public SendOutcome sendPlatformNotification(String templateCode, String eventId, Map<String, String> params) {
        NotificationTemplate template = requireEnabledTemplate(templateCode);
        Notification notification = buildNotification(templateCode, eventId, "USER", PLATFORM_RECEIVER_ID,
                template, params);
        return persistAndEmail(notification, () -> properties.platformEmail());
    }

    private NotificationTemplate requireEnabledTemplate(String templateCode) {
        NotificationTemplate template = templateMapper.selectOne(Wrappers.<NotificationTemplate>query()
                .eq("template_code", templateCode).eq("enabled", 1));
        if (template == null) {
            throw new BusinessException(NotificationErrorCode.TEMPLATE_NOT_FOUND);
        }
        return template;
    }

    private Notification buildNotification(String templateCode, String eventId, String receiverType, Long receiverId,
                                           NotificationTemplate template, Map<String, String> params) {
        Notification notification = new Notification();
        notification.setTemplateCode(templateCode);
        notification.setEventId(eventId);
        notification.setReceiverType(receiverType);
        notification.setReceiverId(receiverId);
        notification.setTitle(renderer.render(template.getTitle(), params));
        notification.setContent(renderer.render(template.getBody(), params));
        notification.setInAppStatus("SENT");
        return notification;
    }

    /** 落库即站内信送达（uk 幂等兜底返回 null）；收件邮箱落库后才解析；邮件结果回写，失败/跳过不影响站内信。 */
    private SendOutcome persistAndEmail(Notification notification, Supplier<String> email) {
        try {
            notificationMapper.insert(notification);
        } catch (DuplicateKeyException e) {
            log.info("[通知] 事件已通知过，跳过: eventId={}", notification.getEventId());
            return null;
        }
        MailChannel.MailResult mailResult = mailChannel.send(email.get(),
                notification.getTitle(), notification.getContent());
        notificationMapper.update(null, Wrappers.<Notification>update()
                .eq("id", notification.getId())
                .set("email_status", mailResult.status())
                .set("email_error", mailResult.error()));
        log.info("[通知] 已发送: eventId={}, receiver={}/{}, 邮件={}", notification.getEventId(),
                notification.getReceiverType(), notification.getReceiverId(), mailResult.status());
        return new SendOutcome(notification.getId(), mailResult.status());
    }

    private String merchantEmail(Long merchantId) {
        var merchant = merchantService.getDetail(merchantId).merchant();
        return merchant == null ? null : merchant.contactEmail();
    }
}
