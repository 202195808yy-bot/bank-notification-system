package com.bank.customer.controller;

import com.bank.common.entity.Customer;
import com.bank.customer.repository.CustomerRepository;
import com.bank.customer.repository.PreferenceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class PreferenceControllerIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private PreferenceRepository preferenceRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

//    @MockBean
//    private StringRedisTemplate redisTemplate;   // 模拟 Redis，避免连接真实 Redis

    @MockBean
    private HashOperations<String, Object, Object> hashOperations;

    private Long testUserId;

    @BeforeEach
    void setUp() {
        // 清空数据
        preferenceRepository.deleteAll();
        customerRepository.deleteAll();

        // 创建测试客户
        Customer customer = new Customer();
        customer.setName("Test");
        customer.setEmail("test@example.com");
        customer.setPhone("+79160000000");
        customer.setPassword(passwordEncoder.encode("123456"));
        customer.setRole("USER");
        customer = customerRepository.save(customer);
        testUserId = customer.getId();

        // 模拟 Redis Hash 操作返回空（表示缓存未命中）
//        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
//        when(hashOperations.entries(anyString())).thenReturn(Map.of()); // 空缓存，强制查库
    }

    @Test
    void getPreferences_ShouldReturnEmptyListForNewUser() throws Exception {
        mockMvc.perform(get("/api/preferences")
                        .header("X-User-Id", testUserId))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    @Test
    void updatePreferences_ShouldPersistAndReturn() throws Exception {
        String json = "[{\"eventType\":\"TRANSACTION\",\"channels\":\"[\\\"SMS\\\"]\",\"enabled\":true}]";

        mockMvc.perform(put("/api/preferences")
                        .header("X-User-Id", testUserId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/preferences")
                        .header("X-User-Id", testUserId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].eventType").value("TRANSACTION"))
                .andExpect(jsonPath("$[0].channels").value("[\"SMS\"]"))
                .andExpect(jsonPath("$[0].enabled").value(true));
    }
}