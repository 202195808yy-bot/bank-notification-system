package com.bank.channel.service;

import com.bank.channel.messaging.StatusProducer;
import com.bank.channel.repository.SentLogRepository;
import com.bank.common.dto.NotificationStatus;
import com.bank.common.dto.SendCommand;
import com.bank.common.entity.SentLog;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class PushSender implements ChannelSender {

    private final SentLogRepository sentLogRepository;
    private final StatusProducer statusProducer;

    /** 真实 FCM 未接（要 Google 项目凭据）。失败率可配，见 PRD-30 */
    @Value("${mock.failure-rate:0.1}")
    private double failureRate;

    @Override
    @CircuitBreaker(name = "pushSender", fallbackMethod = "sendFallback")
    public void send(SendCommand command) {
        if (failureRate > 0 && Math.random() < failureRate) {
            throw new RuntimeException("推送发送失败");
        }
        String response = "Push delivered";
        saveLog(command, "firebase", response, "SENT");
        statusProducer.send(NotificationStatus.success(command.getNotificationId(), "firebase", response));
    }

    private void sendFallback(SendCommand command, Throwable t) {
        saveLog(command, "firebase", t.getMessage(), "FAILED");
        statusProducer.send(NotificationStatus.failure(command.getNotificationId(), "firebase", t.getMessage()));
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