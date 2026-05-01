package com.bank.channel.messaging;

import com.bank.common.dto.SendCommand;
import com.bank.channel.service.ChannelSender;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class SendCommandConsumer {
    @Autowired
    private Map<String, ChannelSender> senderMap;

    @KafkaListener(topics = "notification.send.command", groupId = "channel-service")
    public void onCommand(SendCommand command) {
        String beanName = command.getChannel().toLowerCase() + "Sender";
        ChannelSender sender = senderMap.get(beanName);
        if (sender != null) {
            sender.send(command);
        }
    }
}