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

import org.springframework.beans.factory.annotation.Value;

import java.time.DateTimeException;
import java.time.Duration;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
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
     * 客户没填时区时用来判定免打扰时段的服务端默认时区。
     * ⚠️ 不能再靠容器的本地时间：容器没设 TZ 即 UTC，于是莫斯科客户填 23:00-07:00
     * 实际挡的是本地 02:00-10:00（PRD-43）。
     */
    @Value("${app.business-zone:Europe/Moscow}")
    private String businessZone;

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
            recordSuppressed(event, "NOT_SUBSCRIBED");
            return;
        }
        if (!pref.isEnabled()) {
            recordSuppressed(event, "PREFERENCE_DISABLED");
            return;
        }

        List<String> channels = pref.getChannels();
        if (channels == null || channels.isEmpty()) {
            recordSuppressed(event, "NO_CHANNEL");
            return;
        }

        String dedupKey = AppConstants.DEDUP_PREFIX + event.getEventId() + ":" + customerId;
        Boolean notDup = redisTemplate.opsForValue()
                .setIfAbsent(dedupKey, "1", Duration.ofHours(AppConstants.DEDUP_WINDOW_HOURS));
        if (Boolean.FALSE.equals(notDup)) {
            log.debug("重复事件已忽略: eventId={}, customerId={}", event.getEventId(), customerId);
            return;
        }

        CustomerContact contact = fetchContact(customerId);

        // 免打扰时段：以前直接丢弃、什么都不留，客户与运维都看不出「有事件被抑制过」。
        // ⚠️ 时段内的事件只是「不发」，不会到点补发 —— 没有延迟队列，这点在偏好页文案里写明了。
        ZoneId quietZone = resolveZone(contact);
        if (isQuietTime(pref, quietZone)) {
            log.info("免打扰时段内，渠道全部跳过: customerId={}, eventType={}, zone={}, {}-{}",
                    customerId, eventType, quietZone, pref.getQuietStart(), pref.getQuietEnd());
            channels.forEach(channel -> saveOutcome(event, channel, null, null,
                    SendStatus.SKIPPED, "QUIET_PERIOD"));
            return;
        }

        for (String channel : channels) {
            try {
                String recipient = contact == null ? null : contact.recipientFor(channel);
                if (recipient == null) {
                    // 客户档案里缺这个渠道的地址（例如从未填过 pushToken）：属于数据问题，不是发送失败
                    String reason = contact == null ? "CONTACT_UNAVAILABLE" : contact.missingReasonFor(channel);
                    saveOutcome(event, channel, null, null, SendStatus.FAILED_VALIDATION, reason);
                    continue;
                }

                // 按客户自己的语言取模板（PRD-10/G1）。以前这里写死 AppConstants.DEFAULT_LOCALE，
                // 于是俄语界面的客户收到中文正文。该语言没有模板时回退默认语言，而不是跳过渠道。
                String locale = contact.getLocale() == null || contact.getLocale().isBlank()
                        ? AppConstants.DEFAULT_LOCALE : contact.getLocale();
                NotificationTemplate template =
                        templateRenderService.getTemplate(eventType, channel, locale);
                if (template == null && !AppConstants.DEFAULT_LOCALE.equals(locale)) {
                    log.info("客户语言无可用模板，回退默认语言: customerId={}, eventType={}, channel={}, locale={}",
                            customerId, eventType, channel, locale);
                    locale = AppConstants.DEFAULT_LOCALE;
                    template = templateRenderService.getTemplate(eventType, channel, locale);
                }
                if (template == null) {
                    log.warn("模板缺失，跳过渠道: eventType={}, channel={}, locale={}",
                            eventType, channel, locale);
                    saveOutcome(event, channel, null, null, SendStatus.SKIPPED, "TEMPLATE_MISSING");
                    continue;
                }
                String content = templateRenderService.render(template, event.getPayload());

                // 模板要用的变量 payload 里没有 → 以前把 "{{balance}}" 这种残文直接投给客户，
                // 界面还显示成 SENT。现在按数据问题记失败，不下发。
                List<String> missing = templateRenderService.unresolvedVariables(content);
                if (!missing.isEmpty()) {
                    log.warn("模板变量缺失，未发送: eventType={}, channel={}, templateId={}, variables={}",
                            eventType, channel, template.getId(), missing);
                    saveOutcome(event, channel, template.getId(), null,
                            SendStatus.FAILED_VALIDATION, "VARIABLE_MISSING");
                    continue;
                }

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
                cmd.setSubject(templateRenderService.renderTitle(template, event.getPayload()));
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

    /**
     * 事件到达但被整体抑制（未订阅 / 已关闭 / 偏好里没有渠道）。
     * 这三条分支以前只留一行 log.debug，界面上零记录 —— 管理员在事件发送页看到「发送成功」，
     * 到历史页却什么都搜不到（PRD-07 的残留口径）。现在落一行 SKIPPED 并用 reason 说明原因；
     * channel 写占位值 NONE（而非 null）是因为该列 NOT NULL 且进唯一键，
     * 而确实没有任何渠道被选中，所以也不能伪造成 SMS/EMAIL/PUSH。
     */
    private void recordSuppressed(BankEvent event, String reason) {
        log.info("事件被抑制，未生成通知: customerId={}, eventType={}, eventId={}, reason={}",
                event.getCustomerId(), event.getEventType(), event.getEventId(), reason);
        if (notificationRepository.existsByEventIdAndCustomerId(event.getEventId(), event.getCustomerId())) {
            return;
        }
        saveOutcome(event, AppConstants.SUPPRESSED_CHANNEL, null, null, SendStatus.SKIPPED, reason);
    }

    private String rootMessage(Exception e) {
        Throwable t = e;
        while (t.getCause() != null && t.getCause() != t) {
            t = t.getCause();
        }
        return t.getClass().getSimpleName() + ": " + t.getMessage();
    }

    /**
     * 免打扰时段判断，支持跨零点区间（如 23:00 ~ 07:00）。
     * now 取客户所在时区的墙钟时间，见 resolveZone。
     */
    private boolean isQuietTime(NotificationPreference pref, ZoneId zone) {
        if (pref.getQuietStart() == null || pref.getQuietEnd() == null) {
            return false;
        }
        LocalTime now = LocalTime.now(zone);
        LocalTime start = pref.getQuietStart();
        LocalTime end = pref.getQuietEnd();
        if (start.equals(end)) {
            // 零宽窗口按「不静默」处理。保存时已被 QUIET_PERIOD_EMPTY 拒掉，但修之前留下的旧行
            // 仍是 start==end==00:00 —— 若走下面跨零点那一支，!now.isBefore(00:00) 恒真，
            // 这位客户就会被判定全天免打扰、所有通知静默 SKIPPED。
            return false;
        }
        if (start.isBefore(end)) {
            // 常规区间 [start, end]
            return !now.isBefore(start) && !now.isAfter(end);
        }
        // 跨零点区间 [start, 24:00) ∪ [00:00, end]
        return !now.isBefore(start) || !now.isAfter(end);
    }

    /**
     * 免打扰按「客户那里的钟」判定：优先客户档案里的 IANA 时区，缺失或解析不了才用服务端业务时区。
     * 这里绝不抛异常 —— 配错时区不能让整条派发停下来。
     */
    private ZoneId resolveZone(CustomerContact contact) {
        String customerZone = contact == null ? null : contact.getTimezone();
        if (customerZone != null && !customerZone.isBlank()) {
            try {
                return ZoneId.of(customerZone.trim());
            } catch (DateTimeException e) {
                log.warn("客户时区无法解析，改用业务时区: timezone={}, businessZone={}", customerZone, businessZone);
            }
        }
        try {
            return ZoneId.of(businessZone);
        } catch (DateTimeException e) {
            log.error("app.business-zone 配置非法，退回 UTC 判定免打扰: {}", businessZone);
            return ZoneOffset.UTC;
        }
    }
}
