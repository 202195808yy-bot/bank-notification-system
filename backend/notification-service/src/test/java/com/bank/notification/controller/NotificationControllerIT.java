package com.bank.notification.controller;

import com.bank.common.constant.AppConstants;
import com.bank.common.dto.CustomerContact;
import com.bank.common.entity.Notification;
import com.bank.common.enums.SendStatus;
import com.bank.notification.client.ContactClient;
import com.bank.notification.messaging.SendCommandProducer;
import com.bank.notification.repository.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class NotificationControllerIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private NotificationRepository notificationRepository;

    @MockBean
    private StringRedisTemplate redisTemplate;

    @MockBean
    private ValueOperations<String, String> valueOperations;

    @MockBean
    private ContactClient contactClient;

    @MockBean
    private SendCommandProducer sendCommandProducer;

    private Long failedNotificationId;

    @BeforeEach
    void setUp() {
        notificationRepository.deleteAll();

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenReturn(true);

        CustomerContact contact = new CustomerContact();
        contact.setPhone("79990000000");
        when(contactClient.getContact(any())).thenReturn(contact);

        Notification failed = new Notification();
        failed.setCustomerId(1L);
        failed.setEventType("TRANSACTION");
        failed.setEventId("evt-fail");
        failed.setChannel("SMS");
        failed.setContent("测试失败内容");
        failed.setStatus(SendStatus.FAILED);
        failed.setRetryCount(0);
        failed.setCreatedAt(LocalDateTime.now());
        failed = notificationRepository.save(failed);
        failedNotificationId = failed.getId();
    }

    @Test
    void retry_asAdmin_shouldResetStatusToPending() throws Exception {
        mockMvc.perform(post("/api/notifications/{id}/retry", failedNotificationId)
                        .header("X-User-Id", "1")
                        .header("X-User-Role", AppConstants.ROLE_ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("RETRY_SUBMITTED"));

        Notification updated = notificationRepository.findById(failedNotificationId).orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals(SendStatus.PENDING, updated.getStatus());
        org.junit.jupiter.api.Assertions.assertEquals(1, updated.getRetryCount());
    }

    @Test
    void retry_asUser_shouldBeForbidden() throws Exception {
        mockMvc.perform(post("/api/notifications/{id}/retry", failedNotificationId)
                        .header("X-User-Id", "1")
                        .header("X-User-Role", "USER"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ADMIN_REQUIRED"));

        // 越权请求不能改动记录
        Notification unchanged = notificationRepository.findById(failedNotificationId).orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals(SendStatus.FAILED, unchanged.getStatus());
    }

    @Test
    void retry_beyondLimit_shouldBeRejected() throws Exception {
        Notification notification = notificationRepository.findById(failedNotificationId).orElseThrow();
        notification.setRetryCount(AppConstants.MAX_RETRY_ATTEMPTS);
        notificationRepository.save(notification);

        mockMvc.perform(post("/api/notifications/{id}/retry", failedNotificationId)
                        .header("X-User-Id", "1")
                        .header("X-User-Role", AppConstants.ROLE_ADMIN))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RETRY_LIMIT_EXCEEDED"));
    }

    @Test
    void statsAll_asUser_shouldBeForbidden() throws Exception {
        mockMvc.perform(get("/api/notifications/stats/all")
                        .header("X-User-Id", "1")
                        .header("X-User-Role", "USER"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ADMIN_REQUIRED"));
    }

    @Test
    void list_asUser_shouldOnlySeeOwnRecords() throws Exception {
        Notification other = new Notification();
        other.setCustomerId(2L);
        other.setEventType("TRANSACTION");
        other.setEventId("evt-other");
        other.setChannel("SMS");
        other.setContent("别人的记录");
        other.setStatus(SendStatus.SENT);
        notificationRepository.save(other);

        mockMvc.perform(get("/api/notifications")
                        .header("X-User-Id", "1")
                        .header("X-User-Role", "USER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));

        // customerId 参数对普通用户无效，不能靠传参越界
        mockMvc.perform(get("/api/notifications")
                        .param("customerId", "2")
                        .header("X-User-Id", "1")
                        .header("X-User-Role", "USER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void list_asAdmin_shouldSeeAllRecords() throws Exception {
        Notification other = new Notification();
        other.setCustomerId(2L);
        other.setEventType("TRANSACTION");
        other.setEventId("evt-other");
        other.setChannel("SMS");
        other.setContent("别人的记录");
        other.setStatus(SendStatus.SENT);
        notificationRepository.save(other);

        mockMvc.perform(get("/api/notifications")
                        .header("X-User-Id", "6")
                        .header("X-User-Role", AppConstants.ROLE_ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void stats_shouldReturnZeroFilledStatuses() throws Exception {
        mockMvc.perform(get("/api/notifications/stats")
                        .header("X-User-Id", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.failed").value(1))
                .andExpect(jsonPath("$.skipped").value(0))
                .andExpect(jsonPath("$.failed_validation").value(0));
    }
}
