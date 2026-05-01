package com.bank.eventadapter.service;

import com.bank.common.dto.BankEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Service
public class EventTransformer {

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 将银行核心系统发送的原始 JSON 字符串转换为标准的 BankEvent 对象
     *
     * @param rawJson 原始事件 JSON
     * @return 标准化后的 BankEvent
     */
    public BankEvent transform(String rawJson) {
        try {
            Map<String, Object> raw = objectMapper.readValue(rawJson, Map.class);
            BankEvent event = new BankEvent();
            // 事件 ID 可以由核心系统提供，若没有则自动生成
            String eventId = (String) raw.getOrDefault("eventId", UUID.randomUUID().toString());
            event.setEventId(eventId);
            event.setEventType((String) raw.getOrDefault("eventType", "UNKNOWN"));
            event.setCustomerId(Long.valueOf(String.valueOf(raw.get("customerId"))));
            // 将原始数据整体作为 payload 传递
            event.setPayload(raw);
            event.setTimestamp(LocalDateTime.now());
            return event;
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse event: " + rawJson, e);
        }
    }
}