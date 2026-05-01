package com.bank.notification.messaging;

import com.bank.common.dto.NotificationStatus;
import com.bank.common.entity.Notification;
import com.bank.common.enums.SendStatus;
import com.bank.notification.repository.NotificationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class StatusConsumer {
    @Autowired
    private NotificationRepository notificationRepository;

    @KafkaListener(topics = "notification.status", groupId = "notification-service")
    public void onStatus(NotificationStatus status) {
        notificationRepository.findById(status.getNotificationId()).ifPresent(notification -> {
            notification.setStatus(SendStatus.valueOf(status.getStatus()));
            notification.setUpdatedAt(LocalDateTime.now());
            notificationRepository.save(notification);
        });
    }
}