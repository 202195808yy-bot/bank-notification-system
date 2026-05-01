package com.bank.channel.messaging;

import com.bank.common.dto.NotificationStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class StatusProducer {
    @Autowired
    private KafkaTemplate<String, NotificationStatus> kafkaTemplate;

    public void send(NotificationStatus status) {
        kafkaTemplate.send("notification.status", status.getNotificationId().toString(), status);
    }
}