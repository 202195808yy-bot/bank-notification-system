package com.bank.common.entity;

import com.bank.common.enums.SendStatus;
import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 通知记录
 */
@Data
@Entity
@Table(name = "notifications", indexes = {
        @Index(name = "idx_notifications_customer", columnList = "customer_id, created_at"),
        @Index(name = "idx_notifications_event_customer_channel", columnList = "event_id, customer_id, channel", unique = true)
})
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 客户ID
     */
    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    /**
     * 事件类型
     */
    @Column(name = "event_type", nullable = false, length = 50)
    private String eventType;

    /**
     * 来源事件ID（用于去重）
     */
    @Column(name = "event_id", nullable = false, length = 100)
    private String eventId;

    /** 使用的模板ID */
    @Column(name = "template_id")
    private Long templateId;

    /**
     * 发送渠道
     */
    @Column(nullable = false, length = 10)
    private String channel;

    /** 最终渲染后的通知内容 */
    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    /**
     * 这条通知实际投往的地址（手机号/邮箱/push 令牌）。地址缺失的行（如 NO_PHONE）为 null。
     * 有了它，「输入谁的手机号/邮箱就发给谁」的直发记录在历史页里才说得出发去了哪里。
     * ⚠️ 可空 + 无默认值，所以 ddl-auto=update 追加这一列时在存量表上安全（PRD-18）。
     */
    @Column(name = "recipient", length = 120)
    private String recipient;

    /** 发送状态 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SendStatus status = SendStatus.PENDING;

    /** 已重试次数 */
    @Column(name = "retry_count")
    private int retryCount = 0;

    /**
     * 未发送/发送失败的原因码（如 QUIET_PERIOD、TEMPLATE_MISSING、NO_EMAIL）。
     * 投递成功时为空。前端按 code 渲染本地化文案，不直接显示英文原文。
     */
    @Column(length = 64)
    private String reason;

    /** 客户是否已读。columnDefinition 带默认值，保证 ddl-auto=update 能在非空表上追加该列 */
    @Column(name = "is_read", nullable = false, columnDefinition = "boolean default false")
    private boolean read = false;

    /** 创建时间 */
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    /** 更新时间 */
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}