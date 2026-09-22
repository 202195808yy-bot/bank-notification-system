package com.bank.customer.service;

import com.bank.common.entity.NotificationPreference;
import com.bank.customer.repository.PreferenceRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PreferenceServiceTest {

    @Mock
    private PreferenceRepository repository;
    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private HashOperations<String, Object, Object> hashOperations;
    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private PreferenceService preferenceService;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
    }

    @Test
    void getPreferences_ShouldReturnFromCache_WhenCacheHit() throws Exception {
        Long customerId = 1L;
        String jsonPref = "{\"id\":1,\"customerId\":1,\"eventType\":\"TRANSACTION\",\"channels\":[\"SMS\"],\"enabled\":true}";
        Map<Object, Object> cached = Map.of("TRANSACTION", jsonPref);
        when(hashOperations.entries("pref:1")).thenReturn(cached);

        NotificationPreference pref = new NotificationPreference();
        pref.setId(1L);
        pref.setCustomerId(1L);
        pref.setEventType("TRANSACTION");
        pref.setEnabled(true);
        pref.setChannels(List.of("SMS"));
        when(objectMapper.readValue(jsonPref, NotificationPreference.class)).thenReturn(pref);

        List<NotificationPreference> result = preferenceService.getPreferences(customerId);

        assertEquals(1, result.size());
        assertEquals("TRANSACTION", result.get(0).getEventType());
        // 验证未访问数据库
        verify(repository, never()).findByCustomerId(any());
    }

    @Test
    void getPreferences_ShouldFallbackToDatabase_WhenRedisUnavailable() {
        Long customerId = 1L;
        when(hashOperations.entries(anyString())).thenThrow(new RuntimeException("Redis error"));
        when(repository.findByCustomerId(customerId)).thenReturn(List.of(new NotificationPreference()));

        List<NotificationPreference> result = preferenceService.getPreferences(customerId);

        assertFalse(result.isEmpty());
        verify(repository).findByCustomerId(customerId);
    }
}