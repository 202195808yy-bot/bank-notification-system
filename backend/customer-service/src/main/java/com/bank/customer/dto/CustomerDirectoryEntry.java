package com.bank.customer.dto;

/**
 * 管理员可见的用户目录条目。
 * 只给标识与"各渠道是否可达"，不外泄手机号与 push 令牌原文——
 * 事件模拟器需要知道发给谁，但不需要拿到完整 PII。
 */
public record CustomerDirectoryEntry(
        Long id,
        String name,
        String email,
        String role,
        boolean hasEmail,
        boolean hasPhone,
        boolean hasPushToken
) {
}
