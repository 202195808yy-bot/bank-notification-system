package com.bank.notification.service;

import com.bank.common.entity.Notification;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 铃铛的实时通道：通知进入终态时按客户推一条 SSE 事件。
 *
 * ⚠️ 只推终态（SENT / FAILED / SKIPPED / FAILED_VALIDATION），不推 PENDING ——
 * 派发行一落库就是 PENDING，实测 7~25 毫秒后才回写 SENT；若推 PENDING，
 * 提示音会在"消息还没发出去"的那一刻响，声音与投递结果就不同步了。
 *
 * ⚠️ 登记表在进程内存里，所以本服务只能单实例跑；要横向扩容得换成 Redis 发布订阅，
 * 否则客户连在实例 A、事件由实例 B 产生，事件就丢了。
 */
@Slf4j
@Service
public class NotificationStreamService {

    /** 服务端主动断开的周期；前端 EventSource 语义下客户端会立刻重连，比让连接无限挂着好 */
    private static final long EMITTER_TIMEOUT_MS = 30 * 60 * 1000L;

    private final Map<Long, CopyOnWriteArrayList<SseEmitter>> subscribers = new ConcurrentHashMap<>();

    public SseEmitter subscribe(Long customerId) {
        SseEmitter emitter = new SseEmitter(EMITTER_TIMEOUT_MS);
        subscribers.computeIfAbsent(customerId, k -> new CopyOnWriteArrayList<>()).add(emitter);
        emitter.onCompletion(() -> release(customerId, emitter));
        emitter.onTimeout(() -> {
            release(customerId, emitter);
            emitter.complete();
        });
        emitter.onError(ignored -> release(customerId, emitter));
        try {
            emitter.send(SseEmitter.event().name("connected")
                    .data("{\"customer_id\":" + customerId + "}"));
        } catch (Exception e) {
            log.debug("SSE 建立后立即断开: customerId={}, cause={}", customerId, e.toString());
            release(customerId, emitter);
        }
        return emitter;
    }

    /**
     * 推送一条终态事件。负载只带 id 与状态，不带正文：
     * 界面上的列表/未读数仍以 REST 为准（同一份数据源），避免推送与查询两套口径。
     */
    public void publish(Notification notification) {
        if (notification == null || notification.getCustomerId() == null || notification.getStatus() == null) {
            return;
        }
        CopyOnWriteArrayList<SseEmitter> emitters = subscribers.get(notification.getCustomerId());
        if (emitters == null || emitters.isEmpty()) {
            return;
        }
        String payload = "{\"notification_id\":" + notification.getId()
                + ",\"status\":\"" + notification.getStatus().name() + "\""
                + ",\"channel\":\"" + notification.getChannel() + "\""
                + ",\"event_type\":\"" + notification.getEventType() + "\""
                + ",\"reason\":" + quoteOrNull(notification.getReason()) + "}";
        for (SseEmitter emitter : emitters) {
            try {
                synchronized (emitter) {
                    emitter.send(SseEmitter.event().name("notification").data(payload));
                }
            } catch (IOException | IllegalStateException e) {
                // 客户端关掉页面是常态，不是错误：断开即注销，不刷 WARN
                log.debug("SSE 推送失败，注销该连接: customerId={}, notificationId={}, cause={}",
                        notification.getCustomerId(), notification.getId(), e.toString());
                release(notification.getCustomerId(), emitter);
            }
        }
    }

    /** 心跳：nginx 的 proxy_read_timeout 与网关都会掐掉长时间无数据的连接，注释帧不会被前端解析成事件 */
    @Scheduled(fixedRate = 15000)
    public void heartbeat() {
        subscribers.forEach((customerId, emitters) -> {
            for (SseEmitter emitter : emitters) {
                try {
                    synchronized (emitter) {
                        emitter.send(SseEmitter.event().comment("hb"));
                    }
                } catch (Exception e) {
                    release(customerId, emitter);
                }
            }
            if (emitters.isEmpty()) {
                subscribers.remove(customerId, emitters);
            }
        });
    }

    private void release(Long customerId, SseEmitter emitter) {
        CopyOnWriteArrayList<SseEmitter> emitters = subscribers.get(customerId);
        if (emitters != null && emitters.remove(emitter)) {
            if (emitters.isEmpty()) {
                subscribers.remove(customerId, emitters);
            }
        }
    }

    private String quoteOrNull(String value) {
        return value == null || value.isBlank() ? "null" : "\"" + value + "\"";
    }
}
