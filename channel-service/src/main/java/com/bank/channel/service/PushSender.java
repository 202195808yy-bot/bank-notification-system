package com.bank.channel.service;

import com.bank.common.dto.SendCommand;
import com.bank.common.entity.SentLog;
import com.bank.channel.messaging.StatusProducer;
import com.bank.channel.repository.SentLogRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class PushSender implements ChannelSender {
    @Autowired
    private SentLogRepository sentLogRepository;
    @Autowired
    private StatusProducer statusProducer;

    @Override
    public void send(SendCommand command) {
        String response = "push sent";
        String status = "SENT";
        SentLog log = new SentLog();
        log.setNotificationId(command.getNotificationId());
        log.setProvider("firebase");
        log.setResponse(response);
        log.setStatus(status);
        log.setSentAt(LocalDateTime.now());
        sentLogRepository.save(log);

        statusProducer.send(com.bank.common.dto.NotificationStatus.success(
                command.getNotificationId(), "firebase", response));
    }
}