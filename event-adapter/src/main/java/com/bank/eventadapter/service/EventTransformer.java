package com.bank.eventadapter.service;

import com.bank.common.dto.BankEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;

@Component
public class EventTransformer {

    private final ObjectMapper objectMapper = new ObjectMapper();

    public BankEvent transform(String rawJson) {
        try {
            Map<String, Object> map = objectMapper.readValue(rawJson, Map.class);
            BankEvent event = new BankEvent();
            event.setEventId((String) map.getOrDefault("eventId", java.util.UUID.randomUUID().toString()));
            event.setEventType((String) map.get("eventType"));
            event.setCustomerId(Long.parseLong(map.get("customerId").toString()));
            event.setPayload(extractPayload(map));
            event.setTimestamp(LocalDateTime.now());
            return event;
        } catch (Exception e) {
            throw new RuntimeException("无效的事件格式", e);
        }
    }

    /**
     * 兼容两种事件体：显式的 payload 对象优先，否则把顶层字段整体当作变量表（事件发送页用的是扁平结构）。
     * 只取其中一种，避免把 eventType/customerId 也当成模板变量。
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> extractPayload(Map<String, Object> map) {
        Object nested = map.get("payload");
        return nested instanceof Map ? new java.util.HashMap<>((Map<String, Object>) nested)
                : new java.util.HashMap<>(map);
    }
}