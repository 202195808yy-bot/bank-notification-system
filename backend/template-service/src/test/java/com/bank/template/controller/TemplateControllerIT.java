package com.bank.template.controller;

import com.bank.common.entity.NotificationTemplate;
import com.bank.template.repository.TemplateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class TemplateControllerIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TemplateRepository templateRepository;

    @MockBean
    private StringRedisTemplate redisTemplate;   // 模板服务中也用到了 Redis

    @BeforeEach
    void setUp() {
        templateRepository.deleteAll();
    }

    @Test
    void createTemplate_shouldSucceed() throws Exception {
        String json = "{\"eventType\":\"BILL\",\"channel\":\"SMS\",\"locale\":\"zh_CN\",\"titleTemplate\":\"账单\",\"bodyTemplate\":\"您的账单{{amount}}元\"}";
        mockMvc.perform(post("/api/templates")
                        .header("Authorization", "Bearer mockAdminToken")  // 网关JWT过滤已放行，此处仅占位
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.eventType").value("BILL"));
    }

    @Test
    void updateTemplate_shouldModifyContent() throws Exception {
        // 先创建一个模板
        NotificationTemplate template = new NotificationTemplate();
        template.setEventType("TRANSACTION");
        template.setChannel("EMAIL");
        template.setLocale("zh_CN");
        template.setTitleTemplate("旧标题");
        template.setBodyTemplate("旧内容");
        template = templateRepository.save(template);

        String updateJson = "{\"eventType\":\"TRANSACTION\",\"channel\":\"EMAIL\",\"locale\":\"zh_CN\",\"titleTemplate\":\"新标题\",\"bodyTemplate\":\"新内容\"}";
        mockMvc.perform(put("/api/templates/{id}", template.getId())
                        .header("Authorization", "Bearer mockAdminToken")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateJson))
                .andExpect(status().isOk());

        // 验证数据库
        NotificationTemplate updated = templateRepository.findById(template.getId()).orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals("新标题", updated.getTitleTemplate());
        org.junit.jupiter.api.Assertions.assertEquals("新内容", updated.getBodyTemplate());
    }

    @Test
    void deleteTemplate_shouldRemoveRecord() throws Exception {
        NotificationTemplate template = new NotificationTemplate();
        template.setEventType("PROMOTION");
        template.setChannel("PUSH");
        template.setLocale("zh_CN");
        template.setBodyTemplate("促销内容");
        template = templateRepository.save(template);

        mockMvc.perform(delete("/api/templates/{id}", template.getId())
                        .header("Authorization", "Bearer mockAdminToken"))
                .andExpect(status().isOk());

        org.junit.jupiter.api.Assertions.assertFalse(templateRepository.findById(template.getId()).isPresent());
    }

    @Test
    void listTemplates_shouldReturnAll() throws Exception {
        NotificationTemplate t1 = new NotificationTemplate();
        t1.setEventType("TRANSACTION");
        t1.setChannel("SMS");
        t1.setLocale("zh_CN");
        t1.setBodyTemplate("内容1");
        templateRepository.save(t1);
        NotificationTemplate t2 = new NotificationTemplate();
        t2.setEventType("RISK_ALERT");
        t2.setChannel("EMAIL");
        t2.setLocale("zh_CN");
        t2.setBodyTemplate("内容2");
        templateRepository.save(t2);

        mockMvc.perform(get("/api/templates")
                        .header("Authorization", "Bearer mockAdminToken"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));
    }
}