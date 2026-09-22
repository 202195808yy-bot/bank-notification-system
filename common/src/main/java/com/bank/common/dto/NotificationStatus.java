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
    /**
     * 稳定的机器可读原因码（如 {@code SMS_AMOUNT_NOT_ENOUGH}、{@code SMS_MOCK_SEND}）。
     * <p>{@code providerResponse} 是给流水看的人类文本，会随网关措辞变；界面要按语言翻译、
     * 要能聚合统计，只能靠这个码。以前它不存在，于是阿里云返回的
     * {@code isv.AMOUNT_NOT_ENOUGH} 只活在 {@code sent_logs.response} 里（而那张表没有 API），
     * 客户看到的永远是一个孤零零的「失败」。
     */
    private String reasonCode;

    // 静态工厂方法
    public static NotificationStatus success(Long notificationId, String provider, String providerResponse) {
        return success(notificationId, provider, providerResponse, null);
    }

    public static NotificationStatus success(Long notificationId, String provider,
                                             String providerResponse, String reasonCode) {
        return new NotificationStatus(notificationId, "SENT", provider, providerResponse,
                LocalDateTime.now(), reasonCode);
    }

    public static NotificationStatus failure(Long notificationId, String provider, String providerResponse) {
        return failure(notificationId, provider, providerResponse, null);
    }

    public static NotificationStatus failure(Long notificationId, String provider,
                                             String providerResponse, String reasonCode) {
        return new NotificationStatus(notificationId, "FAILED", provider, providerResponse,
                LocalDateTime.now(), reasonCode);
    }
}