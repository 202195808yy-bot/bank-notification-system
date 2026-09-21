package com.bank.channel.service;

import com.bank.channel.messaging.StatusProducer;
import com.bank.channel.repository.SentLogRepository;
import com.bank.common.dto.NotificationStatus;
import com.bank.common.dto.SendCommand;
import com.bank.common.entity.SentLog;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailSender implements ChannelSender {

    private static final String PROVIDER_MOCK = "smtp-mock";
    private static final String PROVIDER_REAL = "smtp";

    private final SentLogRepository sentLogRepository;
    private final StatusProducer statusProducer;
    /** 真实 SMTP 客户端；用 ObjectProvider 才能在 MAIL_HOST 未配置时正常启动 */
    private final ObjectProvider<JavaMailSender> javaMailSender;

    /** 显式开关：配了 SMTP 也不一定真要发出去（演示/回归时故意不发），所以 real-send 与配置两个条件都要满足 */
    @Value("${mail.real-send:false}")
    private boolean realSend;

    /**
     * Boot 的 MailSenderAutoConfiguration 用 @ConditionalOnProperty(prefix="spring.mail", value="host")，
     * 而它只把字面量 "false" 当作不匹配 —— 空串仍会创建 JavaMailSender bean。
     * compose 里 MAIL_HOST 默认就是空串，所以"有没有配 SMTP"必须由这里判空白，
     * 否则 real-send=true 而 host 为空时会拿空 from 去连服务器，每条邮件都记 FAILED。
     */
    @Value("${spring.mail.host:}")
    private String mailHost;

    /** mock 路径的随机失败率；设 0 即"永不随机失败"（PRD-30 要求成功率数字可信） */
    @Value("${mock.failure-rate:0.1}")
    private double failureRate;

    @Value("${mail.from:${spring.mail.username:}}")
    private String fromAddress;

    @Override
    @CircuitBreaker(name = "emailSender", fallbackMethod = "sendFallback")
    public void send(SendCommand command) {
        JavaMailSender sender = useReal() ? javaMailSender.getIfAvailable() : null;
        if (sender == null) {
            if (realSend) {
                log.warn("mail.real-send=true 但拿不到可用的 JavaMailSender（多半是 spring.mail.host 为空），本条退回模拟发送: notificationId={}",
                        command.getNotificationId());
            }
            if (failureRate > 0 && Math.random() < failureRate) {
                throw new RuntimeException("邮件发送失败");
            }
            saveLog(command, PROVIDER_MOCK, "Email sent", "SENT");
            statusProducer.send(NotificationStatus.success(command.getNotificationId(), PROVIDER_MOCK, "Email sent"));
            return;
        }
        sendReal(sender, command);
    }

    private void sendReal(JavaMailSender sender, SendCommand command) {
        try {
            MimeMessage message = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, StandardCharsets.UTF_8.name());
            helper.setFrom(fromAddress);
            helper.setTo(command.getRecipient());
            helper.setSubject(subjectOf(command));
            helper.setText(command.getContent() == null ? "" : command.getContent());
            sender.send(message);
            log.info("邮件已真实投递: notificationId={}, to={}", command.getNotificationId(), command.getRecipient());
            saveLog(command, PROVIDER_REAL, "Email sent", "SENT");
            statusProducer.send(NotificationStatus.success(command.getNotificationId(), PROVIDER_REAL, "Email sent"));
        } catch (Exception e) {
            // 交给熔断器的 fallback 记 FAILED + 回调状态，这里只补一条带收件人的定位日志
            log.error("邮件真实投递失败: notificationId={}, to={}", command.getNotificationId(), command.getRecipient(), e);
            throw new RuntimeException("邮件发送失败: " + e.getMessage(), e);
        }
    }

    /** 主题用派发端渲染好的 titleTemplate；老消息没有 subject 字段时退回正文首行，避免发出空主题邮件 */
    private String subjectOf(SendCommand command) {
        String subject = command.getSubject();
        if (subject != null && !subject.isBlank()) {
            return subject.trim();
        }
        String content = command.getContent();
        if (content == null || content.isBlank()) {
            return "Банковское уведомление";
        }
        int lineEnd = content.indexOf('\n');
        String firstLine = (lineEnd > 0 ? content.substring(0, lineEnd) : content).trim();
        return firstLine.length() > 60 ? firstLine.substring(0, 60) + "…" : firstLine;
    }

    private void sendFallback(SendCommand command, Throwable t) {
        String provider = useReal() ? PROVIDER_REAL : PROVIDER_MOCK;
        saveLog(command, provider, t.getMessage(), "FAILED");
        statusProducer.send(NotificationStatus.failure(command.getNotificationId(), provider, t.getMessage()));
    }

    /** 真发 = 显式开关打开 且 host 非空白；任一不满足都走 mock，绝不为配置缺失给客户留一条 FAILED */
    private boolean useReal() {
        return realSend && mailHost != null && !mailHost.isBlank();
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
