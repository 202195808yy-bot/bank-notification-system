package com.bank.notification.service;

import com.bank.common.constant.AppConstants;
import com.bank.common.dto.BankEvent;
import com.bank.common.dto.CustomerContact;
import com.bank.common.dto.SendCommand;
import com.bank.common.entity.Notification;
import com.bank.common.entity.NotificationPreference;
import com.bank.common.entity.NotificationTemplate;
import com.bank.common.enums.SendStatus;
import com.bank.notification.client.ContactClient;
import com.bank.notification.client.PreferenceClient;
import com.bank.notification.messaging.SendCommandProducer;
import com.bank.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationDispatchService {

    private final PreferenceClient preferenceClient;
    private final ContactClient contactClient;
    private final StringRedisTemplate redisTemplate;
    private final NotificationRepository notificationRepository;
    private final TemplateRenderService templateRenderService;
    private final SendCommandProducer sendCommandProducer;

    /**
     * 不能加 @Transactional：记录必须先提交入库再发 Kafka，
     * 否则渠道服务会在提交前回调，StatusConsumer 查不到记录导致通知永久停在 PENDING。
     */
    public void dispatch(BankEvent event) {
        Long customerId = event.getCustomerId();
        String eventType = event.getEventType();
        if (customerId == null || eventType == null || eventType.isBlank()) {
            log.warn("事件缺少 customerId/eventType，已丢弃: eventId={}", event.getEventId());
            return;
        }

        List<NotificationPreference> prefs;
        try {
            prefs = preferenceClient.getPreferences(customerId);
        } catch (Exception e) {
            log.error("获取偏好失败，事件未处理: customerId={}, eventId={}", customerId, event.getEventId(), e);
            return;
        }

        NotificationPreference pref = prefs.stream()
                .filter(p -> eventType.equals(p.getEventType()))
                .findFirst()
                .orElse(null);
        if (pref == null) {
            log.debug("客户未订阅该事件类型，无需通知: customerId={}, eventType={}", customerId, eventType);
            return;
        }
        if (!pref.isEnabled()) {
            log.debug("客户已关闭该事件类型的通知: customerId={}, eventType={}", customerId, eventType);
            return;
        }

        List<String> channels = pref.getChannels();
        if (channels == null || channels.isEmpty()) {
            log.warn("偏好未配置任何渠道: customerId={}, eventType={}", customerId, eventType);
            return;
        }

        String dedupKey = AppConstants.DEDUP_PREFIX + event.getEventId() + ":" + customerId;
        Boolean notDup = redisTemplate.opsForValue()
                .setIfAbsent(dedupKey, "1", Duration.ofHours(AppConstants.DEDUP_WINDOW_HOURS));
        if (Boolean.FALSE.equals(notDup)) {
            log.debug("重复事件已忽略: eventId={}, customerId={}", event.getEventId(), customerId);
            return;
        }

        // 免打扰时段：以前直接丢弃、什么都不留，客户与运维都看不出「有事件被抑制过」
        if (isQuietTime(pref)) {
            channels.forEach(channel -> saveOutcome(event, channel, null, null,
                    SendStatus.SKIPPED, "QUIET_PERIOD"));
            return;
        }

        CustomerContact contact = fetchContact(customerId);

        for (String channel : channels) {
            try {
                String recipient = contact == null ? null : contact.recipientFor(channel);
                if (recipient == null) {
                    // 客户档案里缺这个渠道的地址（例如从未填过 pushToken）：属于数据问题，不是发送失败
                    String reason = contact == null ? "CONTACT_UNAVAILABLE" : contact.missingReasonFor(channel);
                    saveOutcome(event, channel, null, null, SendStatus.FAILED_VALIDATION, reason);
                    continue;
                }

                NotificationTemplate template =
                        templateRenderService.getTemplate(eventType, channel, AppConstants.DEFAULT_LOCALE);
                if (template == null) {
                    log.warn("模板缺失，跳过渠道: eventType={}, channel={}, locale={}",
                            eventType, channel, AppConstants.DEFAULT_LOCALE);
                    saveOutcome(event, channel, null, null, SendStatus.SKIPPED, "TEMPLATE_MISSING");
                    continue;
                }
                String content = templateRenderService.render(template, event.getPayload());

                Notification notification = saveOutcome(event, channel, template.getId(), content,
                        SendStatus.PENDING, null);
                if (notification == null) {
                    continue;
                }

                SendCommand cmd = new SendCommand();
                cmd.setNotificationId(notification.getId());
                cmd.setCustomerId(customerId);
                cmd.setChannel(channel);
                cmd.setRecipient(recipient);
                cmd.setContent(content);
                cmd.setTemplateId(template.getId());

                sendCommandProducer.send(cmd);
            } catch (Exception e) {
                log.error("处理渠道 {} 失败", channel, e);
            }
        }
    }

    private CustomerContact fetchContact(Long customerId) {
        try {
            return contactClient.getContact(customerId);
        } catch (Exception e) {
            log.error("获取客户收件地址失败: customerId={}", customerId, e);
            return null;
        }
    }

    /**
     * 落一条处理结果记录（PENDING / SKIPPED / FAILED_VALIDATION）。
     * 返回 null 表示唯一键冲突（同一事件+客户+渠道已处理过）或落库失败。
     */
    private Notification saveOutcome(BankEvent event, String channel, Long templateId, String content,
                                     SendStatus status, String reason) {
        try {
            Notification notification = new Notification();
            notification.setCustomerId(event.getCustomerId());
            notification.setEventType(event.getEventType());
            notification.setEventId(event.getEventId());
            notification.setChannel(channel);
            notification.setTemplateId(templateId);
            notification.setContent(content == null ? "" : content);
            notification.setStatus(status);
            notification.setReason(reason);
            return notificationRepository.save(notification);
        } catch (DataIntegrityViolationException e) {
            // 最常见的是 (event_id, customer_id, channel) 唯一键冲突（重复事件）；
            // 但也可能是 DDL 与枚举不一致（如库里残留旧的 status CHECK 约束），
            // 因此必须把根因打出来，不能一律按「重复」静默吞掉。
            log.warn("通知落库失败，已跳过该渠道: eventId={}, customerId={}, channel={}, status={}, cause={}",
                    event.getEventId(), event.getCustomerId(), channel, status, rootMessage(e));
            return null;
        }
    }

    private String rootMessage(Exception e) {
        Throwable t = e;
        while (t.getCause() != null && t.getCause() != t) {
            t = t.getCause();
        }
        return t.getClass().getSimpleName() + ": " + t.getMessage();
    }

    /**
     * 免打扰时段判断，支持跨零点区间（如 23:00 ~ 07:00）
     */
    private boolean isQuietTime(NotificationPreference pref) {
        if (pref.getQuietStart() == null || pref.getQuietEnd() == null) {
            return false;
        }
        LocalTime now = LocalTime.now();
        LocalTime start = pref.getQuietStart();
        LocalTime end = pref.getQuietEnd();
        if (start.isBefore(end) || start.equals(end)) {
            // 常规区间 [start, end]
            return !now.isBefore(start) && !now.isAfter(end);
        }
        // 跨零点区间 [start, 24:00) ∪ [00:00, end]
        return !now.isBefore(start) || !now.isAfter(end);
    }
}
