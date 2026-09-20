package com.bank.notification.service;

import com.bank.common.constant.AppConstants;
import com.bank.common.entity.NotificationTemplate;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Duration;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class TemplateRenderService {

    private static final Duration TEMPLATE_CACHE_TTL = Duration.ofHours(1);

    private final StringRedisTemplate redisTemplate;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${template.service.url:http://template-service:8083}")
    private String templateServiceUrl;

    /**
     * 获取模板：先查 Redis 缓存，未命中则调用 template-service 并写回缓存（带 TTL）
     */
    public NotificationTemplate getTemplate(String eventType, String channel, String locale) {
        String cacheKey = buildCacheKey(eventType, channel, locale);
        try {
            String json = redisTemplate.opsForValue().get(cacheKey);
            if (json != null) {
                return objectMapper.readValue(json, NotificationTemplate.class);
            }
        } catch (Exception e) {
            log.warn("模板缓存读取失败，将回源 template-service: {}", e.getMessage());
        }

        NotificationTemplate template = fetchFromService(eventType, channel, locale);
        if (template != null) {
            cacheTemplate(cacheKey, template);
        }
        return template;
    }

    /**
     * 渲染模板正文，将 {{变量}} 占位符替换为实际值
     */
    public String render(NotificationTemplate template, Map<String, Object> payload) {
        String body = template.getBodyTemplate();
        if (body == null || payload == null) {
            return body;
        }
        for (Map.Entry<String, Object> entry : payload.entrySet()) {
            body = body.replace("{{" + entry.getKey() + "}}", String.valueOf(entry.getValue()));
        }
        return body;
    }

    private NotificationTemplate fetchFromService(String eventType, String channel, String locale) {
        try {
            String url = UriComponentsBuilder.fromHttpUrl(templateServiceUrl)
                    .path("/api/templates")
                    .queryParam("eventType", eventType)
                    .queryParam("channel", channel)
                    .queryParam("locale", locale)
                    .toUriString();
            NotificationTemplate[] templates =
                    restTemplate.getForObject(url, NotificationTemplate[].class);
            if (templates != null && templates.length > 0) {
                return templates[0];
            }
        } catch (Exception e) {
            log.error("调用 template-service 失败: eventType={}, channel={}", eventType, channel, e);
        }
        return null;
    }

    private void cacheTemplate(String cacheKey, NotificationTemplate template) {
        try {
            redisTemplate.opsForValue().set(cacheKey, objectMapper.writeValueAsString(template), TEMPLATE_CACHE_TTL);
        } catch (Exception e) {
            log.error("缓存模板失败: key={}", cacheKey, e);
        }
    }

    private String buildCacheKey(String eventType, String channel, String locale) {
        return AppConstants.TEMPLATE_CACHE_PREFIX + eventType + ":" + channel + ":" + locale;
    }
}
