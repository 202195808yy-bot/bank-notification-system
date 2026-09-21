package com.bank.common.constant;

public final class AppConstants {
    private AppConstants() {}
    public static final String PREF_CACHE_PREFIX = "pref:";
    public static final String TEMPLATE_CACHE_PREFIX = "template:";
    public static final String DEDUP_PREFIX = "dedup:";
    public static final String DEFAULT_LOCALE = "zh_CN";
    /**
     * 客户可选的通知正文语言，取值必须与 notification_templates.locale 的写法一致（下划线式），
     * 前端同一份列表在 web/src/utils/constants.js 的 NOTIFICATION_LOCALES。
     * ⚠️ 与界面语言（react-intl 的 ru/zh/en 短码）是两套互不相干的值：客户在 /profile 的下拉里
     * 直接选这三个之一，派发端按 customers.locale 取模板，没有、也不应该有任何自动映射（PRD-10）。
     */
    public static final java.util.List<String> SUPPORTED_LOCALES = java.util.List.of("zh_CN", "ru_RU", "en_US");
    public static final String DEFAULT_ROLE = "USER";
    public static final String ROLE_ADMIN = "ADMIN";
    public static final long JWT_EXPIRATION_MS = 3_600_000;
    public static final String TOPIC_BANK_EVENTS = "bank.events";
    public static final String TOPIC_SEND_COMMAND = "notification.send.command";
    public static final String TOPIC_STATUS = "notification.status";
    /**
     * 脏消息落地主题的后缀。".DLT" 不是我们自己选的约定，而是 spring-kafka
     * DeadLetterPublishingRecoverer 的默认目标名（原主题名 + ".DLT"）—— 实测确认过：
     * 把常量写成 "-dlt" 时消息照样落到 bank.events.DLT，只是 NewTopic 白建了三个空主题。
     * 现在由消费端显式给出 destinationResolver，后缀仍以这一个常量为准。
     */
    public static final String DLT_SUFFIX = ".DLT";
    public static final String TOPIC_BANK_EVENTS_DLT = TOPIC_BANK_EVENTS + DLT_SUFFIX;
    public static final String TOPIC_SEND_COMMAND_DLT = TOPIC_SEND_COMMAND + DLT_SUFFIX;
    public static final String TOPIC_STATUS_DLT = TOPIC_STATUS + DLT_SUFFIX;
    public static final int DEDUP_WINDOW_HOURS = 1;
    /** 手动重试上限：超过后必须人工介入，否则失败通知会被无限重投 */
    public static final int MAX_RETRY_ATTEMPTS = 3;
    /**
     * 事件被整体抑制时写入 notifications.channel 的占位值：该事件确实没有任何渠道被选中，
     * 而 channel 列是 NOT NULL 且参与 (event_id, customer_id, channel) 唯一键，留空无法落库。
     * 不是可投递渠道，因此不出现在前端渠道筛选/偏好编辑的 CHANNELS 列表里。
     */
    public static final String SUPPRESSED_CHANNEL = "NONE";
}