package com.bank.eventadapter.messaging;

import com.bank.common.dto.BankEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class EventProducer {
    @Autowired
    private KafkaTemplate<String, BankEvent> kafkaTemplate;

    public void publish(BankEvent event) {
        kafkaTemplate.send("bank.events", event.getEventId(), event);
    }
}