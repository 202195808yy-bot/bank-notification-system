package com.bank.notification.messing;   // 注意：你的包名可以是 com.bank.notification.service

import com.bank.common.dto.BankEvent;
import com.bank.common.dto.CustomerContact;
import com.bank.common.entity.Notification;
import com.bank.common.entity.NotificationPreference;
import com.bank.common.entity.NotificationTemplate;
import com.bank.common.enums.SendStatus;
import com.bank.notification.client.ContactClient;
import com.bank.notification.client.PreferenceClient;
import com.bank.notification.repository.NotificationRepository;
import com.bank.notification.service.NotificationDispatchService;
import com.bank.notification.service.TemplateRenderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@SpringBootTest
class NotificationDispatchServiceIT {

    @Autowired
    private NotificationDispatchService dispatchService;

    @Autowired
    private NotificationRepository notificationRepository;

    @MockBean
    private PreferenceClient preferenceClient;          // 模拟远程偏好服务

    @MockBean
    private ContactClient contactClient;                // 模拟客户联系方式（真实收件人来源）

    @MockBean
    private TemplateRenderService templateRenderService; // 模拟模板渲染

    @MockBean
    private StringRedisTemplate redisTemplate;          // 模拟 Redis，避免依赖真实 Redis

    @MockBean
    private ValueOperations<String, String> valueOperations;

    @BeforeEach
    void setUp() {
        notificationRepository.deleteAll();

        // ========== 模拟偏好 ==========
        NotificationPreference mockPref = new NotificationPreference();
        mockPref.setEventType("TRANSACTION");
        mockPref.setChannels(List.of("SMS"));
        mockPref.setEnabled(true);
        when(preferenceClient.getPreferences(anyLong())).thenReturn(List.of(mockPref));

        // ========== 模拟收件地址：SMS 走真实手机号 ==========
        CustomerContact mockContact = new CustomerContact();
        mockContact.setPhone("79990000000");
        when(contactClient.getContact(anyLong())).thenReturn(mockContact);

        // ========== 模拟模板 ==========
        NotificationTemplate mockTemplate = new NotificationTemplate();
        mockTemplate.setId(1L);
        mockTemplate.setEventType("TRANSACTION");
        mockTemplate.setChannel("SMS");
        mockTemplate.setBodyTemplate("账号 {{account}} 发生交易 {{amount}} 元");
        when(templateRenderService.getTemplate(anyString(), anyString(), anyString())).thenReturn(mockTemplate);
        when(templateRenderService.render(eq(mockTemplate), anyMap())).thenAnswer(invocation -> {
            Map<String, Object> payload = invocation.getArgument(1);
            return "账号 " + payload.get("account") + " 发生交易 " + payload.get("amount") + " 元";
        });

        // ========== 模拟 Redis ==========
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        // 对于去重键 dedup:xxx，返回 false 表示成功设置（即不重复）
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenReturn(true);
        // 对于其他可能用到 Redis 的地方，也做一些安全的模拟（可选）
    }

    @Test
    void dispatch_shouldCreateNotification() {
        BankEvent event = new BankEvent();
        event.setEventId("evt-001");
        event.setEventType("TRANSACTION");
        event.setCustomerId(1L);
        event.setPayload(Map.of("account", "1234", "amount", "500"));
        event.setTimestamp(LocalDateTime.now());

        dispatchService.dispatch(event);

        long count = notificationRepository.count();
        assertTrue(count > 0, "应生成一条通知");
        Optional<Notification> notif = notificationRepository.findAll().stream().findFirst();
        assertTrue(notif.isPresent());
        assertEquals("evt-001", notif.get().getEventId());
        assertEquals("SMS", notif.get().getChannel());
        // 收件地址齐备时才应进入投递流程；缺地址会落成 FAILED_VALIDATION
        assertEquals(SendStatus.PENDING, notif.get().getStatus());
    }

    @Test
    void dispatch_duplicateEvent_shouldBeIgnored() {
        BankEvent event = new BankEvent();
        event.setEventId("evt-002");
        event.setEventType("TRANSACTION");
        event.setCustomerId(2L);
        event.setPayload(Map.of("account", "5678", "amount", "200"));
        event.setTimestamp(LocalDateTime.now());

        // 第一次处理（redis 会返回 true 表示设置成功）
        dispatchService.dispatch(event);
        // 第二次处理时，模拟 Redis 返回 false 表示已存在，从而去重
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenReturn(false);   // 重复事件，锁已存在

        dispatchService.dispatch(event);

        long count = notificationRepository.countByEventId("evt-002");
        assertEquals(1, count, "重复事件不应产生多条通知");
    }
}