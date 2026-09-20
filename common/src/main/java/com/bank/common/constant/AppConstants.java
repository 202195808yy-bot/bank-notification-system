package com.bank.common.constant;

public final class AppConstants {
    private AppConstants() {}
    public static final String PREF_CACHE_PREFIX = "pref:";
    public static final String TEMPLATE_CACHE_PREFIX = "template:";
    public static final String DEDUP_PREFIX = "dedup:";
    public static final String REGISTER_RATE_LIMIT_PREFIX = "rate:register:";
    public static final String DEFAULT_LOCALE = "zh_CN";
    public static final String DEFAULT_ROLE = "USER";
    public static final String ROLE_ADMIN = "ADMIN";
    public static final long JWT_EXPIRATION_MS = 3_600_000;
    public static final String TOPIC_BANK_EVENTS = "bank.events";
    public static final String TOPIC_SEND_COMMAND = "notification.send.command";
    public static final String TOPIC_STATUS = "notification.status";
    public static final int DEDUP_WINDOW_HOURS = 1;
    public static final int REGISTER_RATE_LIMIT = 3;
    /** 手动重试上限：超过后必须人工介入，否则失败通知会被无限重投 */
    public static final int MAX_RETRY_ATTEMPTS = 3;
}