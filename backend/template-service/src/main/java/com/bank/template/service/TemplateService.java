package com.bank.template.service;

import com.bank.common.constant.AppConstants;
import com.bank.common.entity.NotificationTemplate;
import com.bank.common.enums.ChannelType;
import com.bank.common.enums.EventType;
import com.bank.template.exception.TemplateException;
import com.bank.template.repository.TemplateRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TemplateService {

    /**
     * 与读取端 TemplateRenderService.TEMPLATE_CACHE_TTL 一致。
     * ⚠️ 写缓存原先没有 TTL，于是这条缓存永不过期；一旦某次改动只写了缓存没写库
     * （或反过来），坏值会一直用下去。带 TTL 才是可自愈的缓存。
     */
    private static final Duration TEMPLATE_CACHE_TTL = Duration.ofHours(1);

    private static final Set<String> EVENT_TYPES = Arrays.stream(EventType.values())
            .map(Enum::name).collect(Collectors.toUnmodifiableSet());
    private static final Set<String> CHANNELS = Arrays.stream(ChannelType.values())
            .map(Enum::name).collect(Collectors.toUnmodifiableSet());
    private static final int MAX_TITLE_LENGTH = 200;

    private final TemplateRepository templateRepository;
    private final StringRedisTemplate redisTemplate;
    /**
     * ⚠️ 必须是容器注入的这一份：原先写成 {@code new ObjectMapper()}，它没有注册
     * JavaTimeModule，序列化带 LocalDateTime 的模板时抛 InvalidDefinitionException，
     * 被下面的 catch 当成「Redis 不可用、不影响业务」吞掉 —— 结果是改完模板在一小时内
     * 读取端仍拿旧缓存。原先那行手写 new 是整个模板管理「改了没反应」的根因。
     */
    private final ObjectMapper objectMapper;

    public List<NotificationTemplate> findAll(String eventType, String channel, String locale) {
        Specification<NotificationTemplate> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (StringUtils.hasText(eventType)) {
                predicates.add(cb.equal(root.get("eventType"), canonical(eventType)));
            }
            if (StringUtils.hasText(channel)) {
                predicates.add(cb.equal(root.get("channel"), canonical(channel)));
            }
            if (StringUtils.hasText(locale)) {
                predicates.add(cb.equal(root.get("locale"), locale.trim()));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return templateRepository.findAll(spec, Sort.by("eventType", "channel", "locale"));
    }

    @Transactional
    public NotificationTemplate create(NotificationTemplate template) {
        validate(template);
        template.setId(null);
        requireTripleFree(canonical(template.getEventType()), canonical(template.getChannel()),
                template.getLocale(), null);
        NotificationTemplate saved = templateRepository.save(template);
        putCache(saved);
        return saved;
    }

    @Transactional
    public NotificationTemplate update(Long id, NotificationTemplate template) {
        NotificationTemplate existing = requireById(id);
        validate(template);
        String eventType = canonical(template.getEventType());
        String channel = canonical(template.getChannel());
        requireTripleFree(eventType, channel, template.getLocale(), id);

        String previousKey = buildCacheKey(existing.getEventType(), existing.getChannel(), existing.getLocale());
        existing.setTitleTemplate(template.getTitleTemplate());
        existing.setBodyTemplate(template.getBodyTemplate());
        existing.setEventType(eventType);
        existing.setChannel(channel);
        existing.setLocale(template.getLocale());
        NotificationTemplate saved = templateRepository.save(existing);
        // 三元组变了就先把旧 key 清掉，否则旧 key 下那份过期模板还会被读到
        evictCacheFor(previousKey);
        putCache(saved);
        return saved;
    }

    @Transactional
    public void delete(Long id) {
        NotificationTemplate template = requireById(id);
        templateRepository.delete(template);
        evictCacheFor(buildCacheKey(template.getEventType(), template.getChannel(), template.getLocale()));
    }

    // ========== 校验与缓存 ==========

    /**
     * 校验规则与列定义、与枚举、与读取端的取模板方式对齐；
     * 项目没有引 spring-boot-starter-validation（全工程无一处 @Valid），
     * 为了一个页面引入新依赖不划算，这里手写并把结果映射成 400。
     */
    private void validate(NotificationTemplate template) {
        if (template == null) {
            throw new TemplateException(HttpStatus.BAD_REQUEST, "TEMPLATE_REQUIRED", "请求体为空");
        }
        if (!StringUtils.hasText(template.getEventType())) {
            throw new TemplateException(HttpStatus.BAD_REQUEST, "TEMPLATE_EVENT_TYPE_INVALID", "事件类型不能为空");
        }
        if (!StringUtils.hasText(template.getChannel())) {
            throw new TemplateException(HttpStatus.BAD_REQUEST, "TEMPLATE_CHANNEL_INVALID", "渠道不能为空");
        }
        String eventType = canonical(template.getEventType());
        if (!EVENT_TYPES.contains(eventType)) {
            throw new TemplateException(HttpStatus.BAD_REQUEST, "TEMPLATE_EVENT_TYPE_INVALID",
                    "未知事件类型: " + template.getEventType());
        }
        String channel = canonical(template.getChannel());
        if (!CHANNELS.contains(channel)) {
            throw new TemplateException(HttpStatus.BAD_REQUEST, "TEMPLATE_CHANNEL_INVALID",
                    "未知渠道: " + template.getChannel());
        }
        // locale 允许留空（实体默认 zh_CN）；给了就必须是 notification_templates.locale 用过的写法
        String locale = StringUtils.hasText(template.getLocale())
                ? template.getLocale().trim() : AppConstants.DEFAULT_LOCALE;
        if (!AppConstants.SUPPORTED_LOCALES.contains(locale)) {
            throw new TemplateException(HttpStatus.BAD_REQUEST, "TEMPLATE_LOCALE_UNSUPPORTED",
                    "不支持的语言: " + template.getLocale() + "，可选 " + AppConstants.SUPPORTED_LOCALES);
        }
        if (!StringUtils.hasText(template.getBodyTemplate())) {
            throw new TemplateException(HttpStatus.BAD_REQUEST, "TEMPLATE_BODY_REQUIRED", "正文模板不能为空");
        }
        if (template.getTitleTemplate() != null && template.getTitleTemplate().length() > MAX_TITLE_LENGTH) {
            throw new TemplateException(HttpStatus.BAD_REQUEST, "TEMPLATE_TITLE_TOO_LONG",
                    "标题模板最长 " + MAX_TITLE_LENGTH + " 字");
        }
        template.setEventType(eventType);
        template.setChannel(channel);
        template.setLocale(locale);
    }

    /**
     * 事件类型/渠道在库里一律是大写枚举名，而派发端按字符串精确匹配取模板
     * （NotificationDispatchService 用 eventType/channel 拼缓存 key）。
     * 收到小写就原样落库的话，这条模板永远匹配不到事件 —— 所以在此归一。
     */
    private String canonical(String value) {
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private NotificationTemplate requireById(Long id) {
        return templateRepository.findById(id)
                .orElseThrow(() -> new TemplateException(HttpStatus.NOT_FOUND, "TEMPLATE_NOT_FOUND",
                        "模板不存在: id=" + id));
    }

    private void requireTripleFree(String eventType, String channel, String locale, Long excludeId) {
        templateRepository.findFirstByEventTypeAndChannelAndLocale(eventType, channel, locale)
                .filter(other -> !other.getId().equals(excludeId))
                .ifPresent(other -> {
                    if (excludeId == null) {
                        throw new TemplateException(HttpStatus.CONFLICT, "TEMPLATE_EXISTS",
                                "同一 事件类型/渠道/语言 的模板已存在: "
                                        + eventType + "/" + channel + "/" + locale + " (id=" + other.getId() + ")，请改那条或换语言");
                    }
                    throw new TemplateException(HttpStatus.CONFLICT, "TEMPLATE_CONFLICT",
                            "另一条模板已占用该 事件类型/渠道/语言: "
                                    + eventType + "/" + channel + "/" + locale + " (id=" + other.getId() + ")");
                });
    }

    private void putCache(NotificationTemplate template) {
        String key = buildCacheKey(template.getEventType(), template.getChannel(), template.getLocale());
        try {
            redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(template), TEMPLATE_CACHE_TTL);
        } catch (Exception e) {
            // Redis 不可用或序列化失败：库里已经有新值，读取端最坏情况退回 TTL 内的旧缓存
            log.warn("模板缓存写入失败，key={}", key, e);
        }
    }

    private void evictCacheFor(String key) {
        try {
            redisTemplate.delete(key);
        } catch (Exception e) {
            log.warn("模板缓存清除失败，key={}", key, e);
        }
    }

    private String buildCacheKey(String eventType, String channel, String locale) {
        return AppConstants.TEMPLATE_CACHE_PREFIX + eventType + ":" + channel + ":" + locale;
    }
}
