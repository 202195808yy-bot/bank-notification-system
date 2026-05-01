package com.bank.template.service;

import com.bank.common.entity.NotificationTemplate;
import com.bank.template.repository.TemplateRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class TemplateService {
    @Autowired
    private TemplateRepository templateRepository;
    @Autowired
    private StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public List<NotificationTemplate> findAll(String eventType, String channel, String locale) {
        if (eventType == null && channel == null && locale == null) {
            return templateRepository.findAll();
        }
        return templateRepository.findByEventTypeAndChannelAndLocale(eventType, channel, locale);
    }

    public NotificationTemplate create(NotificationTemplate template) {
        NotificationTemplate saved = templateRepository.save(template);
        updateCache(saved);
        return saved;
    }

    public NotificationTemplate update(NotificationTemplate template) {
        NotificationTemplate existing = templateRepository.findById(template.getId())
                .orElseThrow(() -> new RuntimeException("Template not found"));
        existing.setTitleTemplate(template.getTitleTemplate());
        existing.setBodyTemplate(template.getBodyTemplate());
        existing.setEventType(template.getEventType());
        existing.setChannel(template.getChannel());
        existing.setLocale(template.getLocale());
        NotificationTemplate updated = templateRepository.save(existing);
        updateCache(updated);
        return updated;
    }

    public void delete(Long id) {
        NotificationTemplate template = templateRepository.findById(id).orElse(null);
        templateRepository.deleteById(id);
        if (template != null) {
            evictCache(template);
        }
    }

    private void updateCache(NotificationTemplate template) {
        String key = "template:" + template.getEventType() + ":" + template.getChannel() + ":" + template.getLocale();
        try {
            redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(template));
        } catch (Exception e) {
            // log
        }
    }

    private void evictCache(NotificationTemplate template) {
        String key = "template:" + template.getEventType() + ":" + template.getChannel() + ":" + template.getLocale();
        redisTemplate.delete(key);
    }
}