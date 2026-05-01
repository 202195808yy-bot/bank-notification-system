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
        preferenceRepository.deleteByCustomerId(customerId);
        newPrefs.forEach(p -> {
            p.setId(null); // ensure new insert
            p.setCustomerId(customerId);
        });
        preferenceRepository.saveAll(newPrefs);
        redisTemplate.delete("pref:" + customerId);
    }
}