package com.bank.notification.service;

import com.bank.common.entity.NotificationTemplate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(MockitoExtension.class)
class TemplateRenderServiceTest {

    @InjectMocks
    private TemplateRenderService renderService;

    @Test
    void render_ShouldReplacePlaceholders() {
        NotificationTemplate template = new NotificationTemplate();
        template.setBodyTemplate("尊敬的{{name}}，您的账户{{account}}发生交易{{amount}}元");
        Map<String, Object> payload = Map.of("name", "张三", "account", "6222***1234", "amount", "500.00");

        String result = renderService.render(template, payload);

        assertEquals("尊敬的张三，您的账户6222***1234发生交易500.00元", result);
    }
}