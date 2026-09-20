package com.bank.notification.messaging;

import com.bank.common.constant.AppConstants;
import com.bank.common.dto.BankEvent;
import com.bank.notification.service.NotificationDispatchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class BankEventListener {

    private final NotificationDispatchService dispatchService;

    @KafkaListener(topics = AppConstants.TOPIC_BANK_EVENTS, groupId = "notification-service")
    public void onEvent(BankEvent event) {
        log.info("收到银行事件: eventId={}, eventType={}, customerId={}",
                event.getEventId(), event.getEventType(), event.getCustomerId());
        dispatchService.dispatch(event);
    }
}