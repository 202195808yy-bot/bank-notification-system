package com.bank.notification.controller;

import com.bank.common.constant.AppConstants;
import com.bank.common.dto.CustomerContact;
import com.bank.common.dto.SendCommand;
import com.bank.common.entity.Notification;
import com.bank.common.enums.SendStatus;
import com.bank.notification.client.ContactClient;
import com.bank.notification.messaging.SendCommandProducer;
import com.bank.notification.repository.NotificationRepository;
import com.bank.notification.service.NotificationStreamService;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {
    private final NotificationRepository notificationRepository;
    private final SendCommandProducer sendCommandProducer;
    private final ContactClient contactClient;
    private final NotificationStreamService notificationStreamService;

    /**
     * 铃铛的实时通道（SSE）。以前前端只能 15 秒轮询，一条通知最坏要等 15 秒才响，
     * 而浏览器会把后台标签页的定时器节流到约 1 次/分钟 —— 实测后端 7~25 毫秒就投递完了，
     * 慢全慢在这头（PRD-30 的实时性缺口）。
     * 只读自己的连接：客户 id 取自网关注入的头，不接受参数，否则可以旁观别人的通知。
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@RequestHeader("X-User-Id") Long userId) {
        return notificationStreamService.subscribe(userId);
    }

    @GetMapping
    public Page<Notification> list(@RequestParam(defaultValue = "0") int page,
                                   @RequestParam(defaultValue = "10") int size,
                                   @RequestParam(required = false) Long customerId,
                                   @RequestParam(required = false) String eventType,
                                   @RequestParam(required = false) String channel,
                                   @RequestParam(required = false) String status,
                                   @RequestParam(required = false) List<String> statuses,
                                   @RequestParam(required = false) String startDate,
                                   @RequestParam(required = false) String endDate,
                                   @RequestParam(required = false) boolean mine,
                                   @RequestHeader(value = "X-User-Id", required = false) Long userId,
                                   @RequestHeader(value = "X-User-Role", required = false) String role) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        // ADMIN 可查全行（重投、排障都需要先看到记录）；普通用户始终被限制在自己名下，
        // 传入的 customerId 参数对普通用户无效。
        // mine=true 用于铃铛这类"我的通知"入口：管理员也只取自己名下，否则会列到别人的记录，
        // 点已读时再被归属校验拒绝（表现为 403）。
        boolean admin = AppConstants.ROLE_ADMIN.equals(role);
        Long scopeCustomerId = (admin && !mine) ? customerId : userId;
        Specification<Notification> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (scopeCustomerId != null) {
                predicates.add(cb.equal(root.get("customerId"), scopeCustomerId));
            }
            if (eventType != null && !eventType.isEmpty()) {
                predicates.add(cb.equal(root.get("eventType"), eventType));
            }
            if (channel != null && !channel.isEmpty()) {
                predicates.add(cb.equal(root.get("channel"), channel));
            }
            if (status != null && !status.isEmpty()) {
                try {
                    SendStatus sendStatus = SendStatus.valueOf(status.toUpperCase());
                    predicates.add(cb.equal(root.get("status"), sendStatus));
                } catch (IllegalArgumentException ignored) {
                }
            }
            // 铃铛下拉只关心投递过的通知（PENDING/SENT/FAILED），历史页则不带这个参数、看全部
            List<SendStatus> statusFilter = parseStatuses(statuses);
            if (!statusFilter.isEmpty()) {
                predicates.add(root.get("status").in(statusFilter));
            }
            if (startDate != null && !startDate.isEmpty()) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), LocalDateTime.parse(startDate)));
            }
            if (endDate != null && !endDate.isEmpty()) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), LocalDateTime.parse(endDate)));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return notificationRepository.findAll(spec, pageable);
    }

    private List<SendStatus> parseStatuses(List<String> raw) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        List<SendStatus> parsed = new ArrayList<>();
        for (String s : raw) {
            try {
                parsed.add(SendStatus.valueOf(s.toUpperCase()));
            } catch (IllegalArgumentException ignored) {
                // 未知状态直接忽略，与单值 status 参数的既有行为保持一致
            }
        }
        return parsed;
    }

    @GetMapping("/stats")
    public Map<String, Long> stats(@RequestHeader("X-User-Id") Long userId) {
        return toStatusMap(notificationRepository.countByCustomerIdGroupByStatus(userId));
    }

    /** 各状态计数 + 总数；未出现的状态补 0，前端不必判断键是否存在 */
    private Map<String, Long> toStatusMap(List<Object[]> rows) {
        Map<String, Long> map = new HashMap<>();
        for (SendStatus status : SendStatus.values()) {
            map.put(status.name().toLowerCase(), 0L);
        }
        long total = 0;
        for (Object[] row : rows) {
            String s = ((SendStatus) row[0]).name().toLowerCase();
            Long cnt = (Long) row[1];
            map.put(s, cnt);
            total += cnt;
        }
        map.put("total", total);
        return map;
    }

    private ResponseEntity<?> forbidden() {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("code", "ADMIN_REQUIRED"));
    }

    /**
     * 全行统计：管理员用。以前任何登录用户都能读到，且仪表盘默认用的就是它（PRD-02/PRD-14）。
     */
    @GetMapping("/stats/all")
    public ResponseEntity<?> statsAll(@RequestHeader(value = "X-User-Role", required = false) String role) {
        if (!AppConstants.ROLE_ADMIN.equals(role)) {
            return forbidden();
        }
        List<Object[]> rows = notificationRepository.countAllGroupByStatus();
        return ResponseEntity.ok(toStatusMap(rows));
    }

    @GetMapping("/unread-count")
    public Map<String, Long> unreadCount(@RequestHeader("X-User-Id") Long userId) {
        return Map.of("unread", notificationRepository.countUnreadDelivered(userId));
    }

    @PatchMapping("/read-all")
    @Transactional
    public Map<String, Integer> markAllRead(@RequestHeader("X-User-Id") Long userId) {
        return Map.of("updated", notificationRepository.markAllReadForCustomer(userId));
    }

    @PatchMapping("/{id}/read")
    @Transactional
    public ResponseEntity<?> markRead(@PathVariable Long id,
                                      @RequestHeader("X-User-Id") Long userId) {
        Notification notification = notificationRepository.findById(id).orElse(null);
        if (notification == null) {
            return ResponseEntity.notFound().build();
        }
        if (!userId.equals(notification.getCustomerId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "Forbidden"));
        }
        notification.setRead(true);
        notificationRepository.save(notification);
        return ResponseEntity.ok(Map.of("message", "ok"));
    }

    /**
     * 重投失败通知。这是运维动作，只对 ADMIN 开放（以前任何登录用户都能重投别人的通知）；
     * 同时受 MAX_RETRY_ATTEMPTS 限制，避免无限重投骚扰客户。
     */
    @PostMapping("/{id}/retry")
    public ResponseEntity<?> retry(@PathVariable Long id,
                                   @RequestHeader(value = "X-User-Role", required = false) String role) {
        if (!AppConstants.ROLE_ADMIN.equals(role)) {
            return forbidden();
        }
        Notification notification = notificationRepository.findById(id).orElse(null);
        if (notification == null) {
            return ResponseEntity.notFound().build();
        }
        if (notification.getStatus() != SendStatus.FAILED) {
            return ResponseEntity.badRequest().body(Map.of("code", "ONLY_FAILED_RETRYABLE"));
        }
        if (notification.getRetryCount() >= AppConstants.MAX_RETRY_ATTEMPTS) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("code", "RETRY_LIMIT_EXCEEDED",
                            "attempts", notification.getRetryCount()));
        }

        // 收件人以"当初落库的那条"为准：直发行（PRD-51）的地址根本不在客户档案里，
        // 按档案重算会把短信发给另一个人。该列为 null 的存量行仍走原来的档案查询。
        String recipient = notification.getRecipient();
        if (recipient == null || recipient.isBlank()) {
            try {
                CustomerContact contact = contactClient.getContact(notification.getCustomerId());
                recipient = contact.recipientFor(notification.getChannel());
                if (recipient == null) {
                    return ResponseEntity.status(HttpStatus.CONFLICT)
                            .body(Map.of("code", String.valueOf(contact.missingReasonFor(notification.getChannel()))));
                }
            } catch (Exception e) {
                log.error("重投时获取收件地址失败: notificationId={}", id, e);
                return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("code", "CONTACT_UNAVAILABLE"));
            }
        }

        notification.setStatus(SendStatus.PENDING);
        notification.setRetryCount(notification.getRetryCount() + 1);
        notification.setReason(null);
        notification.setUpdatedAt(LocalDateTime.now());
        notificationRepository.save(notification);

        SendCommand cmd = new SendCommand();
        cmd.setNotificationId(notification.getId());
        cmd.setCustomerId(notification.getCustomerId());
        cmd.setChannel(notification.getChannel());
        cmd.setContent(notification.getContent());
        cmd.setTemplateId(notification.getTemplateId());
        cmd.setRecipient(recipient);
        // 事件类型必须带上：短信通道靠它选阿里云模板。载荷带不过来（表里没存），
        // 由 channel-service 复用上一次的 sent_logs.request。
        cmd.setEventType(notification.getEventType());
        sendCommandProducer.send(cmd);

        return ResponseEntity.ok(Map.of("code", "RETRY_SUBMITTED",
                "attempts", notification.getRetryCount()));
    }
}
