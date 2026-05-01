package com.bank.notification.service;

import com.bank.common.dto.BankEvent;
import com.bank.common.dto.SendCommand;
import com.bank.common.entity.Notification;
import com.bank.common.entity.NotificationPreference;
import com.bank.common.entity.NotificationTemplate;
import com.bank.common.enums.SendStatus;
import com.bank.notification.client.PreferenceClient;
import com.bank.notification.messaging.SendCommandProducer;
import com.bank.notification.repository.NotificationRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@Service
public class NotificationDispatchService {
    @Autowired
    private PreferenceClient preferenceClient;
    @Autowired
    private StringRedisTemplate redisTemplate;
    @Autowired
    private NotificationRepository notificationRepository;
    @Autowired
    private SendCommandProducer sendCommandProducer;
    @Autowired
    private TemplateRenderService templateRenderService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Transactional
    public void dispatch(BankEvent event) {
        Long customerId = event.getCustomerId();
        String eventType = event.getEventType();

        // 1. 获取偏好（通过 Feign 调用 customer-service）
        List<NotificationPreference> prefs = preferenceClient.getPreferences(customerId);
        NotificationPreference pref = prefs.stream()
                .filter(p -> p.getEventType().equals(eventType) && p.isEnabled())
                .findFirst().orElse(null);
        if (pref == null) return;

        // 检查免打扰
        if (isQuietTime(pref)) return;

        // 去重
        String dedupKey = "dedup:" + event.getEventId() + ":" + customerId;
        Boolean notDuplicate = redisTemplate.opsForValue().setIfAbsent(dedupKey, "1", Duration.ofHours(1));
        if (Boolean.FALSE.equals(notDuplicate)) return;

        // 解析渠道列表
        List<String> channels;
        try {
            channels = objectMapper.readValue(pref.getChannels(), new TypeReference<List<String>>(){});
        } catch (Exception e) {
            return;
        }

        for (String channel : channels) {
            // 2. 获取模板
            NotificationTemplate template = templateRenderService.getTemplate(eventType, channel, "zh_CN");
            // 3. 渲染内容
            String content = templateRenderService.render(template, event.getPayload());
            // 4. 保存通知
            Notification notification = new Notification();
            notification.setCustomerId(customerId);
            notification.setEventId(event.getEventId());
            notification.setChannel(channel);
            notification.setContent(content);
            notification.setStatus(SendStatus.PENDING);
            notification.setCreatedAt(LocalDateTime.now());
            notification = notificationRepository.save(notification);

            // 5. 发送命令
            SendCommand command = new SendCommand();
            command.setNotificationId(notification.getId());
            command.setCustomerId(customerId);
            command.setChannel(channel);
            command.setRecipient(getRecipient(customerId, channel));
            command.setContent(content);
            command.setTemplateId(template.getId());
            sendCommandProducer.send(command);
        }
    }

    private boolean isQuietTime(NotificationPreference pref) {
        LocalTime now = LocalTime.now();
        return pref.getQuietStart() != null && pref.getQuietEnd() != null &&
                !now.isBefore(pref.getQuietStart()) && !now.isAfter(pref.getQuietEnd());
    }

    private String getRecipient(Long customerId, String channel) {
        // 应实际查询 customer 信息，此处简化
        return "mock@bank.com";
    }
}