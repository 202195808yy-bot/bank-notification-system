package com.bank.eventadapter.service;

import com.bank.common.constant.AppConstants;
import com.bank.common.dto.BankEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Component
public class EventTransformer {

    /** 与 notification-service 派发端共用的三个控制键，不能混进模板变量表 */
    private static final Set<String> CONTROL_KEYS =
            Set.of("directRecipient", "directChannel", "directLocale");

    private static final Pattern EMAIL = Pattern.compile("^[^\\s@,;]+@[^\\s@,;]+\\.[^\\s@,;]{2,}$");
    private static final Pattern PHONE = Pattern.compile("^\\+?[0-9][0-9 \\-()]{6,18}$");

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * @param senderId 网关从 JWT 注入的 X-User-Id。直发（只填收件地址、不选客户）时用它作为
     *                 通知行的归属人，这样这条记录在历史页仍可追溯到"谁发的、发去了哪"。
     */
    @SuppressWarnings("unchecked")
    public BankEvent transform(String rawJson, Long senderId) {
        Map<String, Object> map;
        try {
            map = objectMapper.readValue(rawJson, Map.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("EVENT_JSON_INVALID");
        }

        BankEvent event = new BankEvent();
        event.setEventId((String) map.getOrDefault("eventId", java.util.UUID.randomUUID().toString()));
        event.setEventType((String) map.get("eventType"));
        event.setCustomerId(resolveCustomerId(map, senderId));
        event.setPayload(extractPayload(map));
        event.setTimestamp(LocalDateTime.now());
        applyDirectRecipient(event, map);
        return event;
    }

    private Long resolveCustomerId(Map<String, Object> map, Long senderId) {
        Object raw = map.get("customerId");
        if (raw == null) {
            // 修前这里直接 Long.parseLong(null.toString()) → NPE，被外层 catch 成一句"无效的事件格式"
            if (senderId == null) {
                throw new IllegalArgumentException("EVENT_CUSTOMER_REQUIRED");
            }
            return senderId;
        }
        try {
            return Long.parseLong(raw.toString().trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("EVENT_CUSTOMER_INVALID");
        }
    }

    /**
     * 「输入谁的手机号/邮箱就发给谁」的入口侧解析：地址形状决定渠道，渠道也可显式给出。
     * 判定失败一律 400（错误码进响应体），绝不静默降级成"发给档案里的人"。
     */
    private void applyDirectRecipient(BankEvent event, Map<String, Object> map) {
        Object rawRecipient = map.get("directRecipient");
        if (rawRecipient == null || rawRecipient.toString().isBlank()) {
            return;
        }
        String recipient = rawRecipient.toString().trim();
        Object rawChannel = map.get("directChannel");
        String channel = rawChannel == null || rawChannel.toString().isBlank()
                ? inferChannel(recipient) : rawChannel.toString().trim().toUpperCase();
        if (!"SMS".equals(channel) && !"EMAIL".equals(channel)) {
            // 直发只支持能凭地址投递的两个渠道；PUSH 要设备令牌，那不是"输入谁的令牌就发给谁"
            throw new IllegalArgumentException("DIRECT_CHANNEL_UNSUPPORTED");
        }
        if ("EMAIL".equals(channel) ? !EMAIL.matcher(recipient).matches() : !PHONE.matcher(recipient).matches()) {
            throw new IllegalArgumentException("DIRECT_RECIPIENT_INVALID");
        }
        event.setDirectRecipient(recipient);
        event.setDirectChannel(channel);
        Object rawLocale = map.get("directLocale");
        if (rawLocale != null && !rawLocale.toString().isBlank()) {
            String locale = rawLocale.toString().trim();
            if (!AppConstants.SUPPORTED_LOCALES.contains(locale)) {
                throw new IllegalArgumentException("DIRECT_LOCALE_UNSUPPORTED");
            }
            event.setDirectLocale(locale);
        }
    }

    private String inferChannel(String recipient) {
        if (recipient.contains("@")) {
            return "EMAIL";
        }
        return "SMS";
    }

    /**
     * 兼容两种事件体：显式的 payload 对象优先，否则把顶层字段整体当作变量表（事件发送页用的是扁平结构）。
     * 只取其中一种，避免把 eventType/customerId 也当成模板变量。
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> extractPayload(Map<String, Object> map) {
        Object nested = map.get("payload");
        Map<String, Object> payload = nested instanceof Map
                ? new java.util.HashMap<>((Map<String, Object>) nested)
                : new java.util.HashMap<>(map);
        payload.keySet().removeAll(CONTROL_KEYS);
        return payload;
    }
}
