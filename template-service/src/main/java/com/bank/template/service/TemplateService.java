package com.bank.template.service;

import com.bank.common.entity.NotificationTemplate;
import com.bank.template.repository.TemplateRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class TemplateService {

    private final TemplateRepository templateRepository;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public List<NotificationTemplate> findAll(String eventType, String channel, String locale) {
        if (eventType == null && channel == null && locale == null) {
            return templateRepository.findAll();
        }
        return templateRepository.findByEventTypeAndChannelAndLocale(eventType, channel, locale);
    }

    @Transactional
    public NotificationTemplate create(NotificationTemplate template) {
        Optional<NotificationTemplate> existing = templateRepository
                .findByEventTypeAndChannelAndLocale(template.getEventType(),
                        template.getChannel(), template.getLocale())
                .stream().findFirst();
        NotificationTemplate saved;
        if (existing.isPresent()) {
            NotificationTemplate t = existing.get();
            t.setTitleTemplate(template.getTitleTemplate());
            t.setBodyTemplate(template.getBodyTemplate());
            t.setUpdatedAt(LocalDateTime.now());
            saved = templateRepository.save(t);
        } else {
            template.setCreatedAt(LocalDateTime.now());
            template.setUpdatedAt(LocalDateTime.now());
            saved = templateRepository.save(template);
        }

        updateCacheAndNotify(saved, "CREATED");
        return saved;
    }

    @Transactional
    public NotificationTemplate update(NotificationTemplate template) {
        NotificationTemplate existing = templateRepository.findById(template.getId())
                .orElseThrow(() -> new RuntimeException("Template not found"));
        existing.setTitleTemplate(template.getTitleTemplate());
        existing.setBodyTemplate(template.getBodyTemplate());
        existing.setEventType(template.getEventType());
        existing.setChannel(template.getChannel());
        existing.setLocale(template.getLocale());
        existing.setUpdatedAt(LocalDateTime.now());
        NotificationTemplate updated = templateRepository.save(existing);
        updateCacheAndNotify(updated, "UPDATED");
        return updated;
    }

    @Transactional
    public void delete(Long id) {
        NotificationTemplate template = templateRepository.findById(id).orElse(null);
        templateRepository.deleteById(id);
        if (template != null) {
            evictCacheAndNotify(template, "DELETED");
        }
    }

    // ========== 私有辅助方法（已加保护） ==========

    private void updateCacheAndNotify(NotificationTemplate template, String action) {
        String key = buildCacheKey(template);
        try {
            redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(template));
            redisTemplate.convertAndSend("template-cache-sync", key + ":" + action);
        } catch (Exception e) {
            // Redis 不可用时只记日志，不影响主流程
            log.warn("缓存更新失败（不影响业务），key={}", key, e);
        }
    }

    private void evictCacheAndNotify(NotificationTemplate template, String action) {
        String key = buildCacheKey(template);
        try {
            redisTemplate.delete(key);
            redisTemplate.convertAndSend("template-cache-sync", key + ":" + action);
        } catch (Exception e) {
            // Redis 不可用时只记日志，不影响主流程
            log.warn("缓存清除失败（不影响业务），key={}", key, e);
        }
    }

    private String buildCacheKey(NotificationTemplate template) {
        return "template:" + template.getEventType() + ":" +
                template.getChannel() + ":" + template.getLocale();
    }
}