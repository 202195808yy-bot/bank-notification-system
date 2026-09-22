package com.bank.template.repository;

import com.bank.common.entity.NotificationTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface TemplateRepository extends JpaRepository<NotificationTemplate, Long>,
        JpaSpecificationExecutor<NotificationTemplate> {

    Optional<NotificationTemplate> findFirstByEventTypeAndChannelAndLocale(String eventType, String channel, String locale);
}
