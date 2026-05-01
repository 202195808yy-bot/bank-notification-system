package com.bank.notification.controller;

import com.bank.common.entity.Notification;
import com.bank.notification.repository.NotificationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {
    @Autowired
    private NotificationRepository notificationRepository;

    @GetMapping
    public Page<Notification> list(@RequestParam(defaultValue = "0") int page,
                                   @RequestParam(defaultValue = "10") int size,
                                   @RequestParam(required = false) String eventType,
                                   @RequestParam(required = false) String channel,
                                   @RequestParam(required = false) String status) {
        // 简化查询，实际可扩展 Specification
        return notificationRepository.findAll(PageRequest.of(page, size));
    }

    @PostMapping("/{id}/retry")
    public void retry(@PathVariable Long id) {
        // 重试逻辑略，可调用 DispatchService 重新发送
    }

    @GetMapping("/stats")
    public Map<String, Long> stats() {
        long total = notificationRepository.count();
        long pending = notificationRepository.countByStatus("PENDING");
        long sent = notificationRepository.countByStatus("SENT");
        long failed = notificationRepository.countByStatus("FAILED");
        return Map.of("total", total, "pending", pending, "sent", sent, "failed", failed);
    }
}