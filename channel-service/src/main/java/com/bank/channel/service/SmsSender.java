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
public class SmsSender implements ChannelSender {

    private final SentLogRepository sentLogRepository;
    private final StatusProducer statusProducer;

    @Override
    @CircuitBreaker(name = "smsSender", fallbackMethod = "sendFallback")
    public void send(SendCommand command) {
        // 模拟发送短信
        if (Math.random() > 0.9) {
            throw new RuntimeException("短信发送失败");
        }
        String response = "SMS sent OK";
        saveLog(command, "aliyun-sms", response, "SENT");
        statusProducer.send(NotificationStatus.success(command.getNotificationId(), "aliyun-sms", response));
    }

    private void sendFallback(SendCommand command, Throwable t) {
        saveLog(command, "aliyun-sms", t.getMessage(), "FAILED");
        statusProducer.send(NotificationStatus.failure(command.getNotificationId(), "aliyun-sms", t.getMessage()));
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