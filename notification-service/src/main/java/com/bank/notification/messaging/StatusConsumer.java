package com.bank.notification.messaging;

import com.bank.common.constant.AppConstants;
import com.bank.common.dto.NotificationStatus;
import com.bank.common.entity.Notification;
import com.bank.common.enums.SendStatus;
import com.bank.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class StatusConsumer {

    private final NotificationRepository notificationRepository;

    @KafkaListener(topics = AppConstants.TOPIC_STATUS,
            groupId = "notification-service",
            containerFactory = "statusListenerContainerFactory")
    public void onStatus(NotificationStatus status) {
        if (status == null) {
            throw new IllegalStateException("投递回调反序列化失败，转入 " + AppConstants.TOPIC_STATUS_DLT);
        }
        final SendStatus sendStatus;
        try {
            sendStatus = SendStatus.valueOf(status.getStatus());
        } catch (IllegalArgumentException e) {
            log.error("回调状态非法，已忽略: notificationId={}, status={}",
                    status.getNotificationId(), status.getStatus());
            return;
        }
        Notification notification = notificationRepository.findById(status.getNotificationId()).orElse(null);
        if (notification == null) {
            log.error("回调的通知记录不存在，状态未应用: notificationId={}", status.getNotificationId());
            return;
        }
        notification.setStatus(sendStatus);
        notification.setUpdatedAt(LocalDateTime.now());
        notificationRepository.save(notification);
        log.info("通知状态更新: notificationId={}, status={}", status.getNotificationId(), sendStatus);
    }
}