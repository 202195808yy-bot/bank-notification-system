package com.bank.notification.client;

import com.bank.common.entity.NotificationPreference;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;

import java.util.List;

@FeignClient(name = "customer-service", url = "http://localhost:8081")
public interface PreferenceClient {
    @GetMapping("/api/preferences")
    List<NotificationPreference> getPreferences(@RequestHeader("X-User-Id") Long userId);
}