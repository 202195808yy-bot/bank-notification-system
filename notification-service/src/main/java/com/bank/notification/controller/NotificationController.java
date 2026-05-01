package com.bank.notification.controller;

import com.bank.common.dto.SendCommand;
import com.bank.common.entity.Notification;
import com.bank.common.enums.SendStatus;
import com.bank.notification.messaging.SendCommandProducer;
import com.bank.notification.repository.NotificationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private SendCommandProducer sendCommandProducer;   // 注入 Kafka 生产者

    @GetMapping
    public Page<Notification> list(@RequestParam(defaultValue = "0") int page,
                                   @RequestParam(defaultValue = "10") int size,
                                   @RequestHeader("X-User-Id") Long userId) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return notificationRepository.findByCustomerId(userId, pageable);
    }

    @GetMapping("/stats")
    public Map<String, Long> stats(@RequestHeader("X-User-Id") Long userId) {
        long total = notificationRepository.countByCustomerId(userId);
        long pending = notificationRepository.countByCustomerIdAndStatus(userId, SendStatus.PENDING);
        long sent = notificationRepository.countByCustomerIdAndStatus(userId, SendStatus.SENT);
        long failed = notificationRepository.countByCustomerIdAndStatus(userId, SendStatus.FAILED);
        return Map.of("total", total, "pending", pending, "sent", sent, "failed", failed);
    }

    @PostMapping("/{id}/retry")
    public ResponseEntity<?> retry(@PathVariable Long id) {
        Notification notification = notificationRepository.findById(id).orElse(null);
        if (notification == null) {
            return ResponseEntity.notFound().build();
        }
        if (notification.getStatus() != SendStatus.FAILED) {
            return ResponseEntity.badRequest().body("只有失败的通知才能重发");
        }

        // 重置为待发送
        notification.setStatus(SendStatus.PENDING);
        notification.setRetryCount(notification.getRetryCount() + 1);
        notification.setUpdatedAt(LocalDateTime.now());
        notificationRepository.save(notification);

        // 构建发送命令并发送到 Kafka
        SendCommand command = new SendCommand();
        command.setNotificationId(notification.getId());
        command.setCustomerId(notification.getCustomerId());
        command.setChannel(notification.getChannel());
        command.setContent(notification.getContent());
        command.setTemplateId(notification.getTemplateId());
        command.setRecipient(getRecipient(notification.getCustomerId(), notification.getChannel()));

        sendCommandProducer.send(command);

        return ResponseEntity.ok("重发已提交");
    }

    /**
     * 获取接收方地址（模拟实现，实际应从 customer-service 查询客户信息）
     */
    private String getRecipient(Long customerId, String channel) {
        // 简单返回 mock 地址，可根据需要扩展
        return switch (channel.toUpperCase()) {
            case "SMS" -> "mock-phone@bank.com";
            case "EMAIL" -> "mock-email@bank.com";
            case "PUSH" -> "mock-push-token";
            default -> "mock@bank.com";
        };
    }
}