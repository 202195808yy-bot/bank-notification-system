package com.bank.channel.service;

import com.bank.channel.messaging.StatusProducer;
import com.bank.channel.repository.SentLogRepository;
import com.bank.common.dto.NotificationStatus;
import com.bank.common.dto.SendCommand;
import com.bank.common.entity.SentLog;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class EmailSender implements ChannelSender {

    private final SentLogRepository sentLogRepository;
    private final StatusProducer statusProducer;

    @Override
    @CircuitBreaker(name = "emailSender", fallbackMethod = "sendFallback")
    public void send(SendCommand command) {
        if (Math.random() > 0.9) {
            throw new RuntimeException("邮件发送失败");
        }
        String response = "Email sent";
        saveLog(command, "smtp", response, "SENT");
        statusProducer.send(NotificationStatus.success(command.getNotificationId(), "smtp", response));
    }

    private void sendFallback(SendCommand command, Throwable t) {
        saveLog(command, "smtp", t.getMessage(), "FAILED");
        statusProducer.send(NotificationStatus.failure(command.getNotificationId(), "smtp", t.getMessage()));
    }

    private void saveLog(SendCommand command, String provider, String response, String status) {
        SentLog log = new SentLog();
        log.setNotificationId(command.getNotificationId());
        log.setProvider(provider);
        log.setResponse(response);
        log.setStatus(status);
        log.setSentAt(LocalDateTime.now());
        sentLogRepository.save(log);
    }
}