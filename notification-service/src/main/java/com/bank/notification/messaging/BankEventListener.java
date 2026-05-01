package com.bank.notification.messaging;

import com.bank.common.dto.BankEvent;
import com.bank.notification.service.NotificationDispatchService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class BankEventListener {
    @Autowired
    private NotificationDispatchService dispatchService;

    @KafkaListener(topics = "bank.events", groupId = "notification-service")
    public void onEvent(BankEvent event) {
        dispatchService.dispatch(event);
    }
}