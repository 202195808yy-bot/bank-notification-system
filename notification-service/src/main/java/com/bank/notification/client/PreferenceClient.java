package com.bank.notification.client;

import com.bank.common.entity.NotificationPreference;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;

import java.util.List;

@FeignClient(name = "customer-service", url = "${customer.service.url:http://customer-service:8081}")
public interface PreferenceClient {

    /**
     * 获取指定客户的偏好列表
     */
    @GetMapping("/api/preferences")
    List<NotificationPreference> getPreferences(@RequestHeader("X-User-Id") Long userId);
}