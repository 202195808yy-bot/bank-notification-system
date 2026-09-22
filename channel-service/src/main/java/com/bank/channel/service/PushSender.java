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

    /**
     * ⚠️ 与邮件/短信一致：模拟路径必须留下 {@code -mock} 后缀。
     * 以前这里把 provider 写成 "firebase"，于是 sent_logs 里全是"某厂商已送达"，
     * 而实际上没有任何一次真实调用 —— 这正是 PRD-30 说的"送达证据不诚实"。
     */
    private static final String PROVIDER_MOCK = "firebase-mock";

    @Override
    @CircuitBreaker(name = "pushSender", fallbackMethod = "sendFallback")
    public void send(SendCommand command) {
        if (failureRate > 0 && Math.random() < failureRate) {
            throw new ProviderRejectException("PUSH_MOCK_RANDOM_FAIL", "推送发送失败（模拟随机失败）");
        }
        String response = "Push delivered";
        saveLog(command, PROVIDER_MOCK, response, "SENT");
        statusProducer.send(NotificationStatus.success(command.getNotificationId(),
                PROVIDER_MOCK, response, "PUSH_MOCK_SEND"));
    }

    private void sendFallback(SendCommand command, Throwable t) {
        String reasonCode = t instanceof ProviderRejectException reject
                ? reject.getReasonCode()
                : "PUSH_SEND_FAILED";
        saveLog(command, PROVIDER_MOCK, t.getMessage(), "FAILED");
        statusProducer.send(NotificationStatus.failure(command.getNotificationId(),
                PROVIDER_MOCK, t.getMessage(), reasonCode));
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