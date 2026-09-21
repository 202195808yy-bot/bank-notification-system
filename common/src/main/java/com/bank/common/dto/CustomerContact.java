package com.bank.common.dto;

import lombok.Data;

/**
 * 客户在各渠道上的收件地址。由 customer-service 提供，notification-service 派发时读取
 * （修复前派发端写死 mock-phone/mock@bank.com/mock-token，Customer 表里的真实字段从未被使用）。
 */
@Data
public class CustomerContact {

    private String email;
    private String phone;
    private String pushToken;
    /** 客户选择的正文语言；null 表示未设置，由派发端回退到 AppConstants.DEFAULT_LOCALE */
    private String locale;
    /** 客户所在时区（IANA 名）；null 表示未知，由派发端回退到 app.business-zone */
    private String timezone;

    /**
     * 取该渠道实际可用的收件地址；缺少对应字段时返回 null，由调用方判定为无法投递。
     */
    public String recipientFor(String channel) {
        if (channel == null) {
            return null;
        }
        return switch (channel.toUpperCase()) {
            case "SMS" -> blankToNull(phone);
            case "EMAIL" -> blankToNull(email);
            case "PUSH" -> blankToNull(pushToken);
            default -> null;
        };
    }

    /** 该渠道缺字段的错误码，供通知记录的 reason 字段使用 */
    public String missingReasonFor(String channel) {
        if (channel == null) {
            return "UNKNOWN_CHANNEL";
        }
        return switch (channel.toUpperCase()) {
            case "SMS" -> blankToNull(phone) == null ? "NO_PHONE" : null;
            case "EMAIL" -> blankToNull(email) == null ? "NO_EMAIL" : null;
            case "PUSH" -> blankToNull(pushToken) == null ? "NO_PUSH_TOKEN" : null;
            default -> "UNKNOWN_CHANNEL";
        };
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
