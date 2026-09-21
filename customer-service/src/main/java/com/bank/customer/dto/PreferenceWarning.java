package com.bank.customer.dto;

/**
 * 偏好保存后的可达性警告：该渠道已订阅，但客户档案里缺少这个渠道的收件地址。
 * reason 与通知记录用的是同一套码（NO_PHONE / NO_EMAIL / NO_PUSH_TOKEN / UNKNOWN_CHANNEL），
 * 所以前端直接走 enum.reason.* 本地化，不需要再建一套词表。
 */
public record PreferenceWarning(
        Long preferenceId,
        String eventType,
        String channel,
        String reason
) {
}
