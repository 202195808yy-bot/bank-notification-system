package com.bank.common.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 渠道发送状态回执，由 channel-service 发回 notification-service
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class NotificationStatus {

    private Long notificationId;
    private String status;            // "SENT" 或 "FAILED"
    private String provider;
    private String providerResponse;
    private LocalDateTime timestamp;

    // 静态工厂方法
    public static NotificationStatus success(Long notificationId, String provider, String providerResponse) {
        return new NotificationStatus(notificationId, "SENT", provider, providerResponse, LocalDateTime.now());
    }

    public static NotificationStatus failure(Long notificationId, String provider, String providerResponse) {
        return new NotificationStatus(notificationId, "FAILED", provider, providerResponse, LocalDateTime.now());
    }
}