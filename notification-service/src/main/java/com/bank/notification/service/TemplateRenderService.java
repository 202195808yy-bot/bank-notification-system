package com.bank.notification.service;

import com.bank.common.entity.NotificationTemplate;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Service
public class TemplateRenderService {
    @Autowired
    private StringRedisTemplate redisTemplate;
    @Autowired
    private RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public NotificationTemplate getTemplate(String eventType, String channel, String locale) {
        String cacheKey = "template:" + eventType + ":" + channel + ":" + locale;
        String json = redisTemplate.opsForValue().get(cacheKey);
        if (json != null) {
            try {
                return objectMapper.readValue(json, NotificationTemplate.class);
            } catch (Exception ignored) {}
        }
        // 通过 template-service 的 REST API 获取模板（Feign 也适用）
        String url = "http://localhost:8083/api/templates?eventType=" + eventType +
                     "&channel=" + channel + "&locale=" + locale;
        NotificationTemplate template = restTemplate.getForObject(url, NotificationTemplate.class);
        if (template != null) {
            try {
                redisTemplate.opsForValue().set(cacheKey, objectMapper.writeValueAsString(template));
            } catch (Exception ignored) {}
        }
        return template;
    }

    public String render(NotificationTemplate template, Map<String, Object> payload) {
        String body = template.getBodyTemplate();
        if (payload != null) {
            for (Map.Entry<String, Object> entry : payload.entrySet()) {
                body = body.replace("{{" + entry.getKey() + "}}", String.valueOf(entry.getValue()));
            }
        }
        return body;
    }
}