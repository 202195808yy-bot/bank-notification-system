package com.bank.notification.service;

import com.bank.common.constant.AppConstants;
import com.bank.common.dto.BankEvent;
import com.bank.common.dto.CustomerContact;
import com.bank.common.dto.SendCommand;
import com.bank.common.entity.Notification;
import com.bank.common.entity.NotificationPreference;
import com.bank.common.entity.NotificationTemplate;
import com.bank.common.enums.SendStatus;
import com.bank.common.util.Masking;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
    private final NotificationStreamService streamService;

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

        // 「输入谁的手机号/邮箱就发给谁」：入口已经把地址与渠道判定并校验过（PRD-51/N47）。
        // 这条路径刻意不查偏好、不查客户档案、不判免打扰 —— 查了就会出现"地址填对了但被 NOT_SUBSCRIBED 挡掉"，
        // 那不是直发，那是又一次静默抑制。
        if (event.getDirectRecipient() != null && !event.getDirectRecipient().isBlank()) {
            dispatchDirect(event);
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

        if (alreadySeen(event)) {
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
                    contact == null ? null : contact.recipientFor(channel),
                    SendStatus.SKIPPED, "QUIET_PERIOD"));
            return;
        }

        for (String channel : channels) {
            // 按客户自己的语言取模板（PRD-10/G1）。以前这里写死 AppConstants.DEFAULT_LOCALE，
            // 于是俄语界面的客户收到中文正文。该语言没有模板时回退默认语言，而不是跳过渠道。
            String locale = contact == null || contact.getLocale() == null || contact.getLocale().isBlank()
                    ? AppConstants.DEFAULT_LOCALE : contact.getLocale();
            deliver(event, channel,
                    contact == null ? null : contact.recipientFor(channel),
                    // 客户档案里缺这个渠道的地址（例如从未填过 pushToken）：属于数据问题，不是发送失败
                    contact == null ? "CONTACT_UNAVAILABLE" : contact.missingReasonFor(channel),
                    locale, contact);
        }
    }

    /**
     * 单个渠道的「渲染 → 落库 → 下发」。常规派发与直发共用同一条实现，所以失败口径必然一致：
     * 缺地址 = {@code FAILED_VALIDATION}、缺模板 = {@code SKIPPED}、缺变量 = {@code FAILED_VALIDATION}，
     * 只有真正下发命令才落 {@code PENDING}。
     *
     * @param recipientMissingReason recipient 为 null 时写进 reason 的错误码
     * @param contact                档案上下文，用于把 {{account}} 解析成"这个收件人"的账号；直发为 null
     */
    private void deliver(BankEvent event, String channel, String recipient,
                         String recipientMissingReason, String locale, CustomerContact contact) {
        try {
            String eventType = event.getEventType();
            if (recipient == null || recipient.isBlank()) {
                saveOutcome(event, channel, null, null, null,
                        SendStatus.FAILED_VALIDATION, recipientMissingReason);
                return;
            }

            NotificationTemplate template = templateRenderService.getTemplate(eventType, channel, locale);
            if (template == null && !AppConstants.DEFAULT_LOCALE.equals(locale)) {
                log.info("该语言无可用模板，回退默认语言: customerId={}, eventType={}, channel={}, locale={}",
                        event.getCustomerId(), eventType, channel, locale);
                locale = AppConstants.DEFAULT_LOCALE;
                template = templateRenderService.getTemplate(eventType, channel, locale);
            }
            if (template == null) {
                log.warn("模板缺失，跳过渠道: eventType={}, channel={}, locale={}", eventType, channel, locale);
                saveOutcome(event, channel, null, null, recipient, SendStatus.SKIPPED, "TEMPLATE_MISSING");
                return;
            }
            Map<String, Object> vars = varsWithAccount(event, contact, channel, recipient);
            String content = templateRenderService.render(template, vars);

            // 模板要用的变量 payload 里没有 → 以前把 "{{balance}}" 这种残文直接投给客户，
            // 界面还显示成 SENT。现在按数据问题记失败，不下发。
            List<String> missing = templateRenderService.unresolvedVariables(content);
            if (!missing.isEmpty()) {
                log.warn("模板变量缺失，未发送: eventType={}, channel={}, templateId={}, variables={}",
                        eventType, channel, template.getId(), missing);
                saveOutcome(event, channel, template.getId(), null, recipient,
                        SendStatus.FAILED_VALIDATION, "VARIABLE_MISSING");
                return;
            }

            Notification notification = saveOutcome(event, channel, template.getId(), content, recipient,
                    SendStatus.PENDING, null);
            if (notification == null) {
                return;
            }

            SendCommand cmd = new SendCommand();
            cmd.setNotificationId(notification.getId());
            cmd.setCustomerId(event.getCustomerId());
            cmd.setChannel(channel);
            cmd.setRecipient(recipient);
            cmd.setContent(content);
            cmd.setSubject(templateRenderService.renderTitle(template, vars));
            cmd.setTemplateId(template.getId());
            cmd.setEventType(eventType);
            // 与渲染同一份变量表：SMS 渠道的模板参数是从这里取的，另算一份就会出现
            // "正文里是 ****1234、短信模板里是别的值"（PRD-57 连带影响）
            cmd.setVariables(vars);

            sendCommandProducer.send(cmd);
        } catch (Exception e) {
            log.error("处理渠道 {} 失败", channel, e);
        }
    }

    /**
     * 直发（PRD-51/N47）：管理员在事件发送页直接填了收件地址。
     * ⚠️ 与常规派发的差别不是"多一个渠道"，而是**绕开了三道闸门**：偏好订阅、客户档案、免打扰时段。
     * 因此它只能挂在 ADMIN-only 的 {@code POST /api/events} 上（网关 {@code ADMIN_PATH_PREFIXES} 已覆盖），
     * 且仍然保留 1 小时去重，避免同一个 eventId 被重复点成多条真实短信。
     * 通知行照旧落库（customer_id = 事件归属人／操作人），收件地址写进 {@code recipient} 列以便审计。
     */
    private void dispatchDirect(BankEvent event) {
        if (alreadySeen(event)) {
            return;
        }
        String channel = event.getDirectChannel();
        if (channel == null || channel.isBlank()) {
            saveOutcome(event, AppConstants.SUPPRESSED_CHANNEL, null, null, event.getDirectRecipient(),
                    SendStatus.FAILED_VALIDATION, "DIRECT_CHANNEL_MISSING");
            return;
        }
        String locale = event.getDirectLocale() == null || event.getDirectLocale().isBlank()
                ? AppConstants.DEFAULT_LOCALE : event.getDirectLocale();
        log.info("直发事件: eventId={}, 归属客户={}, channel={}, locale={}",
                event.getEventId(), event.getCustomerId(), channel, locale);
        deliver(event, channel, event.getDirectRecipient(), "DIRECT_RECIPIENT_MISSING", locale, null);
    }

    /** 同一 eventId + 同一客户在去重窗口内只处理一次 */
    private boolean alreadySeen(BankEvent event) {
        String dedupKey = AppConstants.DEDUP_PREFIX + event.getEventId() + ":" + event.getCustomerId();
        Boolean notDup = redisTemplate.opsForValue()
                .setIfAbsent(dedupKey, "1", Duration.ofHours(AppConstants.DEDUP_WINDOW_HOURS));
        if (Boolean.FALSE.equals(notDup)) {
            log.debug("重复事件已忽略: eventId={}, customerId={}", event.getEventId(), event.getCustomerId());
            return true;
        }
        return false;
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
     * {{account}} 按**收件人**解析（PRD-57/N60）。优先顺序：
     * ① 客户档案里的账号（掩码后）→ ② 事件载荷自带的 account（同样掩码）→ ③ 收件地址掩码。
     * <p>
     * 以前正文里的 account 只可能来自载荷，于是管理员代发一次事件，载荷里那串账号就被投给
     * 该事件命中的**所有**收件人 —— 张三收到的通知写着李四的账号。改完仍然保留 ② 这一档，
     * 是因为真实的上游系统确实会带交易账号，只是它排在"这个人自己的账号"后面。
     * <p>
     * 返回的是副本，不改 {@code event.getPayload()}：重试与其它消费者还要看到上游原值。
     */
    private Map<String, Object> varsWithAccount(BankEvent event, CustomerContact contact,
                                                String channel, String recipient) {
        Map<String, Object> payload = event.getPayload();
        Map<String, Object> vars = payload == null ? new LinkedHashMap<>() : new LinkedHashMap<>(payload);
        String account = accountFor(contact, channel, recipient, payload);
        if (account != null) {
            vars.put("account", account);
        }
        return vars;
    }

    /** 三档都收口成掩码：进正文的账号永远只出后四位，完整卡号属于 PII */
    private String accountFor(CustomerContact contact, String channel, String recipient,
                              Map<String, Object> payload) {
        if (contact != null) {
            String fromProfile = Masking.last4(contact.getAccountNumber());
            if (fromProfile != null) {
                return fromProfile;
            }
        }
        Object supplied = payload == null ? null : payload.get("account");
        if (supplied != null) {
            String fromPayload = Masking.last4(String.valueOf(supplied));
            if (fromPayload != null) {
                return fromPayload;
            }
        }
        // PUSH 的收件地址是设备令牌，不是账号，这里刻意返回 null：宁可记 VARIABLE_MISSING，
        // 也不要在正文里编一个不存在于任何账本上的"账号"
        return Masking.recipientHint(channel, recipient);
    }

    /**
     * 落一条处理结果记录（PENDING / SKIPPED / FAILED_VALIDATION）。
     * 返回 null 表示唯一键冲突（同一事件+客户+渠道已处理过）或落库失败。
     */
    private Notification saveOutcome(BankEvent event, String channel, Long templateId, String content,
                                    String recipient, SendStatus status, String reason) {
        try {
            Notification notification = new Notification();
            notification.setCustomerId(event.getCustomerId());
            notification.setEventType(event.getEventType());
            notification.setEventId(event.getEventId());
            notification.setChannel(channel);
            notification.setTemplateId(templateId);
            notification.setContent(content == null ? "" : content);
            notification.setRecipient(recipient);
            notification.setStatus(status);
            notification.setReason(reason);
            Notification saved = notificationRepository.save(notification);
            // SKIPPED / FAILED_VALIDATION 在派发这一刻就是终态（后面没有回调），
            // 不推的话铃铛要等到下一次轮询才看得到，而 PENDING 交给 StatusConsumer 推。
            if (status != SendStatus.PENDING) {
                streamService.publish(saved);
            }
            return saved;
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
        saveOutcome(event, AppConstants.SUPPRESSED_CHANNEL, null, null, null,
                SendStatus.SKIPPED, reason);
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
