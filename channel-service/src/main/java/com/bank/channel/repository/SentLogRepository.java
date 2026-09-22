package com.bank.channel.repository;

import com.bank.common.entity.SentLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SentLogRepository extends JpaRepository<SentLog, Long> {

    /**
     * 最近一次带请求体的流水。重投时 {@code notifications} 里没有原始载荷（只存渲染好的正文），
     * 但多变量短信必须按变量传，所以复用上一次的 {@code request}。
     */
    Optional<SentLog> findFirstByNotificationIdAndRequestIsNotNullOrderByIdDesc(Long notificationId);
}