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
public class SmsSender implements ChannelSender {

    private final SentLogRepository sentLogRepository;
    private final StatusProducer statusProducer;

    /** 真实短信网关未接（要阿里云凭据）。失败率可配：设 MOCK_FAILURE_RATE=0 后成功率数字才可用（PRD-30） */
    @Value("${mock.failure-rate:0.1}")
    private double failureRate;

    @Override
    @CircuitBreaker(name = "smsSender", fallbackMethod = "sendFallback")
    public void send(SendCommand command) {
        // 模拟发送短信
        if (failureRate > 0 && Math.random() < failureRate) {
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