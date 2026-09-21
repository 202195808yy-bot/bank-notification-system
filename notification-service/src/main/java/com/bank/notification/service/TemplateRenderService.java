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
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class TemplateRenderService {

    private static final Duration TEMPLATE_CACHE_TTL = Duration.ofHours(1);

    private static final Pattern UNRESOLVED_PLACEHOLDER = Pattern.compile("\\{\\{\\s*([\\w.]+)\\s*}}");

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
        return replaceVars(template.getBodyTemplate(), payload);
    }

    /** 渲染标题，供需要主题的通道（邮件）使用 */
    public String renderTitle(NotificationTemplate template, Map<String, Object> payload) {
        return replaceVars(template.getTitleTemplate(), payload);
    }

    private String replaceVars(String text, Map<String, Object> payload) {
        if (text == null || payload == null) {
            return text;
        }
        String rendered = text;
        for (Map.Entry<String, Object> entry : payload.entrySet()) {
            rendered = rendered.replace("{{" + entry.getKey() + "}}", String.valueOf(entry.getValue()));
        }
        return rendered;
    }

    /**
     * 渲染后仍残留的 {{变量}}。payload 与模板之间没有契约时，缺失的占位符原本会被
     * 当成普通文本一路投递给客户（PRD-35），派发方据此改记失败而不是发送残文。
     */
    public List<String> unresolvedVariables(String rendered) {
        if (rendered == null || rendered.isEmpty()) {
            return List.of();
        }
        return UNRESOLVED_PLACEHOLDER.matcher(rendered).results()
                .map(m -> m.group(1))
                .distinct()
                .toList();
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
