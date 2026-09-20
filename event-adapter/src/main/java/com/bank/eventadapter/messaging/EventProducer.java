package com.bank.eventadapter.messaging;

import com.bank.common.constant.AppConstants;
import com.bank.common.dto.BankEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class EventProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publish(BankEvent event) {
        kafkaTemplate.send(AppConstants.TOPIC_BANK_EVENTS, event.getEventId(), event);
    }
}