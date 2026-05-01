package com.bank.notification.messaging;

import com.bank.common.dto.SendCommand;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class SendCommandProducer {
    @Autowired
    private KafkaTemplate<String, SendCommand> kafkaTemplate;

    public void send(SendCommand command) {
        kafkaTemplate.send("notification.send.command", command.getNotificationId().toString(), command);
    }
}