package com.bank.template.repository;

import com.bank.common.entity.NotificationTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TemplateRepository extends JpaRepository<NotificationTemplate, Long> {
    List<NotificationTemplate> findByEventTypeAndChannelAndLocale(String eventType, String channel, String locale);
}