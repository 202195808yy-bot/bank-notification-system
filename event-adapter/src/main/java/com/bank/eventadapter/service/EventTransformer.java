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

    public BankEvent transform(String rawJson) {
        try {
            Map<String, Object> raw = objectMapper.readValue(rawJson, Map.class);
            BankEvent event = new BankEvent();
            event.setEventId(UUID.randomUUID().toString());
            event.setEventType((String) raw.getOrDefault("type", "UNKNOWN"));
            event.setCustomerId(Long.valueOf(String.valueOf(raw.get("customerId"))));
            event.setPayload(raw);
            event.setTimestamp(LocalDateTime.now());
            return event;
        } catch (Exception e) {
            throw new RuntimeException("Invalid event format", e);
        }
    }
}