package com.bank.eventadapter.controller;

import com.bank.common.dto.BankEvent;
import com.bank.eventadapter.messaging.EventProducer;
import com.bank.eventadapter.service.EventTransformer;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/events")
@RequiredArgsConstructor
public class EventController {

    private final EventTransformer eventTransformer;
    private final EventProducer eventProducer;

    @PostMapping
    public ResponseEntity<?> receiveEvent(@RequestBody String rawJson) {
        try {
            BankEvent event = eventTransformer.transform(rawJson);
            eventProducer.publish(event);
            return ResponseEntity.ok(Map.of("message", "事件已发布"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", "事件格式错误: " + e.getMessage()));
        }
    }
}