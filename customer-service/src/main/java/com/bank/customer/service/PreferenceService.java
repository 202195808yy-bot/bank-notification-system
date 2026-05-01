package com.bank.customer.service;

import com.bank.common.entity.NotificationPreference;
import com.bank.customer.repository.PreferenceRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class PreferenceService {
    @Autowired
    private PreferenceRepository preferenceRepository;
    @Autowired
    private StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public List<NotificationPreference> getPreferences(Long customerId) {
        String cacheKey = "pref:" + customerId;
        Map<Object, Object> entries = redisTemplate.opsForHash().entries(cacheKey);
        if (!entries.isEmpty()) {
            return entries.values().stream().map(v -> {
                try {
                    return objectMapper.readValue((String) v, NotificationPreference.class);
                } catch (JsonProcessingException e) {
                    return null;
                }
            }).filter(Objects::nonNull).collect(Collectors.toList());
        }
        List<NotificationPreference> prefs = preferenceRepository.findByCustomerId(customerId);
        Map<String, String> map = new HashMap<>();
        for (NotificationPreference p : prefs) {
            try {
                map.put(p.getEventType(), objectMapper.writeValueAsString(p));
            } catch (JsonProcessingException ignored) {}
        }
        if (!map.isEmpty()) {
            redisTemplate.opsForHash().putAll(cacheKey, map);
        }
        return prefs;
    }

    @Transactional
    public void updatePreferences(Long customerId, List<NotificationPreference> newPrefs) {
        // 先删除该客户的所有旧偏好
        preferenceRepository.deleteByCustomerId(customerId);
        preferenceRepository.flush();   // 确保删除立即生效

        // 强制将新偏好设置为插入（id 置 null）
        newPrefs.forEach(p -> {
            p.setId(null);
            p.setCustomerId(customerId);
        });

        // 批量插入
        preferenceRepository.saveAll(newPrefs);

        // 清除缓存
        redisTemplate.delete("pref:" + customerId);
    }
}