package com.bank.common.util;

/**
 * 通知正文里账号/收件地址的掩码。规则只有一条：任何进正文的标识都要短到不足以复原原值 ——
 * 短信、邮件、PUSH 三条链路都会把内容留在第三方平台上，完整卡号属于 PII（PRD-57）。
 */
public final class Masking {

    private static final String HIDDEN = "****";

    private Masking() {
    }

    /** 后四位可见；长度不足四位的全隐藏（返回原值就等于没有掩码）。null/空白 → null */
    public static String last4(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed.length() <= 4 ? HIDDEN : HIDDEN + trimmed.substring(trimmed.length() - 4);
    }

    /**
     * 档案里没有账号时，用收件地址本身回答"这条是发给谁的"：
     * 手机号留后四位，邮箱留首字符与域名，PUSH 令牌返回 null —— 设备令牌不是账号，
     * 拿它拼出 {@code ****a1b2} 是在正文里编一个不存在于任何账本上的"账号"。
     */
    public static String recipientHint(String channel, String recipient) {
        if (recipient == null || recipient.isBlank()) {
            return null;
        }
        String value = recipient.trim();
        if ("EMAIL".equalsIgnoreCase(channel)) {
            int at = value.indexOf('@');
            if (at > 0) {
                return value.charAt(0) + HIDDEN + value.substring(at);
            }
        }
        return last4(value);
    }
}
