package com.bank.channel.messaging;

import com.bank.channel.service.ChannelSender;
import com.bank.channel.service.EmailSender;
import com.bank.channel.service.PushSender;
import com.bank.channel.service.SmsSender;
import com.bank.common.constant.AppConstants;
import com.bank.common.dto.SendCommand;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class SendCommandConsumer {

    private final Map<String, ChannelSender> senderMap;

    public SendCommandConsumer(SmsSender smsSender, EmailSender emailSender, PushSender pushSender) {
        senderMap = Map.of(
                "sms", smsSender,
                "email", emailSender,
                "push", pushSender
        );
    }

    @KafkaListener(topics = AppConstants.TOPIC_SEND_COMMAND,
            groupId = "channel-service",
            containerFactory = "sendCommandListenerContainerFactory")
    public void onCommand(SendCommand command) {
        if (command == null) {
            throw new IllegalStateException("投递命令反序列化失败，转入 " + AppConstants.TOPIC_SEND_COMMAND_DLT);
        }
        ChannelSender sender = senderMap.get(command.getChannel().toLowerCase());
        if (sender != null) {
            sender.send(command);
        }
    }
}