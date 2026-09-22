package com.bank.common.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 通知消息模板
 */
@Data
@Entity
@Table(name = "notification_templates",
        uniqueConstraints = @UniqueConstraint(columnNames = {"event_type", "channel", "locale"}))
public class NotificationTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 事件类型
     */
    @Column(name = "event_type", nullable = false, length = 50)
    private String eventType;

    /**
     * 发送渠道
     */
    @Column(nullable = false, length = 10)
    private String channel;

    /**
     * 语言/地区
     */
    @Column(length = 10)
    private String locale = "zh_CN";

    /**
     * 标题模板（支持 {{变量}} 占位）
     */
    @Column(name = "title_template", length = 200)
    private String titleTemplate;

    /**
     * 正文模板（支持 {{变量}} 占位）
     */
    @Column(name = "body_template", columnDefinition = "TEXT", nullable = false)
    private String bodyTemplate;

    /**
     * 创建时间
     */
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
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