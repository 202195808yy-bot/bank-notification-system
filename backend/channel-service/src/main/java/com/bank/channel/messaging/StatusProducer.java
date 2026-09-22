package com.bank.channel.messaging;

import com.bank.common.constant.AppConstants;
import com.bank.common.dto.NotificationStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class StatusProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void send(NotificationStatus status) {
        kafkaTemplate.send(AppConstants.TOPIC_STATUS, status.getNotificationId().toString(), status);
    }
}