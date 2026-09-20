package com.bank.notification.messaging;

import com.bank.common.constant.AppConstants;
import com.bank.common.dto.SendCommand;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SendCommandProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    /**
     * 将发送命令发布到 Kafka 主题
     */
    public void send(SendCommand command) {
        kafkaTemplate.send(AppConstants.TOPIC_SEND_COMMAND,
                command.getNotificationId().toString(),
                command);
    }
}