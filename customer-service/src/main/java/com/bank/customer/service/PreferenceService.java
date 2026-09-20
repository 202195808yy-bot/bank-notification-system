package com.bank.customer.service;

import com.bank.common.constant.AppConstants;
import com.bank.common.entity.NotificationPreference;
import com.bank.customer.repository.PreferenceRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PreferenceService {

    private final PreferenceRepository preferenceRepository;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public List<NotificationPreference> getPreferences(Long customerId) {
        String cacheKey = AppConstants.PREF_CACHE_PREFIX + customerId;
        try {
            Map<Object, Object> entries = redisTemplate.opsForHash().entries(cacheKey);
            if (!entries.isEmpty()) {
                return entries.values().stream()
                        .map(v -> {
                            try {
                                return objectMapper.readValue((String) v, NotificationPreference.class);
                            } catch (JsonProcessingException e) {
                                log.warn("Cache deserialization failed", e);
                                return null;
                            }
                        })
                        .filter(Objects::nonNull)
                        .collect(Collectors.toList());
            }
        } catch (Exception e) {
            log.warn("Redis unavailable, falling back to database", e);
        }

        List<NotificationPreference> prefs = preferenceRepository.findByCustomerId(customerId);
        try {
            Map<String, String> map = new HashMap<>();
            for (NotificationPreference p : prefs) {
                map.put(p.getEventType(), objectMapper.writeValueAsString(p));
            }
            if (!map.isEmpty()) {
                redisTemplate.opsForHash().putAll(cacheKey, map);
            }
        } catch (Exception e) {
            log.warn("Failed to cache preferences", e);
        }
        return prefs;
    }

    @Transactional
    public void updatePreferences(Long customerId, List<NotificationPreference> newPrefs) {
        // 1. 按事件类型去重（同一客户同一事件只保留最后一条）
        Map<String, NotificationPreference> unique = new LinkedHashMap<>();
        for (NotificationPreference p : newPrefs) {
            p.setId(null);
            p.setCustomerId(customerId);
            unique.put(p.getEventType(), p);
        }
        List<NotificationPreference> finalList = new ArrayList<>(unique.values());

        // 2. 删除该客户所有旧偏好
        preferenceRepository.deleteByCustomerId(customerId);
        preferenceRepository.flush();

        // 3. 逐条保存，内部冲突自动跳过
        for (NotificationPreference pref : finalList) {
            try {
                preferenceRepository.saveAndFlush(pref);
            } catch (DataIntegrityViolationException e) {
                log.warn("Duplicate preference ignored: customerId={}, eventType={}", customerId, pref.getEventType());
            }
        }

        // 4. 清除缓存
        try {
            redisTemplate.delete(AppConstants.PREF_CACHE_PREFIX + customerId);
        } catch (Exception e) {
            log.warn("Failed to clear cache", e);
        }
    }
}