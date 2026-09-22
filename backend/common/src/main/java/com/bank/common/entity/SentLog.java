package com.bank.common.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 发送日志
 */
@Data
@Entity
@Table(name = "sent_logs")
public class SentLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 关联的通知ID
     */
    @Column(name = "notification_id", nullable = false)
    private Long notificationId;

    /**
     * 渠道提供商
     */
    @Column(length = 50)
    private String provider;

    /** 请求报文 */
    @Column(columnDefinition = "TEXT")
    private String request;

    /** 响应报文 */
    @Column(columnDefinition = "TEXT")
    private String response;

    /**
     * 渠道返回状态
     */
    @Column(nullable = false, length = 20)
    private String status;

    /** 发送时间 */
    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    @PrePersist
    protected void onCreate() {
        sentAt = LocalDateTime.now();
    }
}