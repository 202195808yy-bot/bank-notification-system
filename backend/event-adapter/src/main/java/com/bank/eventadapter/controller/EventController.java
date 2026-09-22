package com.bank.eventadapter.controller;

import com.bank.common.dto.BankEvent;
import com.bank.eventadapter.messaging.EventProducer;
import com.bank.eventadapter.service.EventTransformer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/events")
@RequiredArgsConstructor
public class EventController {

    private final EventTransformer eventTransformer;
    private final EventProducer eventProducer;

    /**
     * 接收业务事件。返回码的语义在 PRD-05 里被收紧过：
     * <ul>
     *   <li>400 —— 载荷本身不合法（JSON、customerId、直发地址形状），重试同一份内容不会成功；</li>
     *   <li>503 —— 载荷合法但**没能进队列**，调用方应当重试（修前这种情况照样返回 200，
     *       于是 2026-09-21 那次 Kafka 故障里 10 个事件全部静默丢失）；</li>
     *   <li>200 —— broker 已确认，响应体里带 partition/offset，可用来核对消费进度。</li>
     * </ul>
     */
    @PostMapping
    public ResponseEntity<?> receiveEvent(
            @RequestBody String rawJson,
            @RequestHeader(value = "X-User-Id", required = false) Long senderId) {
        BankEvent event;
        try {
            event = eventTransformer.transform(rawJson, senderId);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("code", e.getMessage()));
        } catch (Exception e) {
            log.error("事件解析失败", e);
            return ResponseEntity.badRequest().body(Map.of("code", "EVENT_JSON_INVALID"));
        }

        try {
            RecordMetadata md = eventProducer.publish(event);
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("message", "事件已发布");
            body.put("eventId", event.getEventId());
            body.put("topic", md.topic());
            body.put("partition", md.partition());
            body.put("offset", md.offset());
            return ResponseEntity.ok(body);
        } catch (Exception e) {
            // 带完整事件标识：这是"事件没进队列"时唯一的留痕，运维据此重投
            log.error("事件未能进入 Kafka，请重投: eventId={}, eventType={}, customerId={}",
                    event.getEventId(), event.getEventType(), event.getCustomerId(), e);
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of(
                    "code", "EVENT_PUBLISH_FAILED",
                    "message", "消息队列暂不可用，事件未入队，请重试",
                    "eventId", event.getEventId()));
        }
    }
}
