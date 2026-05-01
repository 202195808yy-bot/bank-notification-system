package com.bank.common.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDateTime;

public class NotificationStatus {

    private Long notificationId;
    private String status;         // "SENT" or "FAILED"
    private String provider;
    private String providerResponse;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private LocalDateTime timestamp;

    public NotificationStatus() {}

    // ===== 静态工厂方法 =====
    public static NotificationStatus success(Long notificationId, String provider, String providerResponse) {
        NotificationStatus ns = new NotificationStatus();
        ns.notificationId = notificationId;
        ns.status = "SENT";
        ns.provider = provider;
        ns.providerResponse = providerResponse;
        ns.timestamp = LocalDateTime.now();
        return ns;
    }

    public static NotificationStatus failure(Long notificationId, String provider, String providerResponse) {
        NotificationStatus ns = new NotificationStatus();
        ns.notificationId = notificationId;
        ns.status = "FAILED";
        ns.provider = provider;
        ns.providerResponse = providerResponse;
        ns.timestamp = LocalDateTime.now();
        return ns;
    }

    // ===== Getters & Setters =====
    public Long getNotificationId() { return notificationId; }
    public void setNotificationId(Long notificationId) { this.notificationId = notificationId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }
    public String getProviderResponse() { return providerResponse; }
    public void setProviderResponse(String providerResponse) { this.providerResponse = providerResponse; }
    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
}