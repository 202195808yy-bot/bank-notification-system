package com.bank.notification.messaging;

import com.bank.common.constant.AppConstants;
import com.bank.common.dto.NotificationStatus;
import com.bank.common.entity.Notification;
import com.bank.common.enums.SendStatus;
import com.bank.notification.repository.NotificationRepository;
import com.bank.notification.service.NotificationStreamService;
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
    private final NotificationStreamService streamService;

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
        // 渠道回执的原因码落库：以前 providerResponse 全仓库无人消费，阿里云的
        // isv.AMOUNT_NOT_ENOUGH 只活在 sent_logs.response（那张表没有 API），界面只剩一个"失败"。
        // 成功且无原因码时显式清空，避免重投成功后还挂着上一次的失败原因。
        notification.setReason(status.getReasonCode());
        notification.setUpdatedAt(LocalDateTime.now());
        notificationRepository.save(notification);
        log.info("通知状态更新: notificationId={}, status={}", status.getNotificationId(), sendStatus);
        // SENT / FAILED 都是终态：这一刻才是"真的发出去了/真的失败了"，推给铃铛。
        // 提示音由前端按状态决定（只在 SENT 响），所以声音与投递结果同源。
        streamService.publish(notification);
    }
}