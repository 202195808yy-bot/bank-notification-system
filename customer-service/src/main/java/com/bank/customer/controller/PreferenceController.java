package com.bank.customer.controller;

import com.bank.common.entity.NotificationPreference;
import com.bank.customer.service.PreferenceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/preferences")
public class PreferenceController {
    @Autowired
    private PreferenceService preferenceService;

    @GetMapping
    public List<NotificationPreference> getPreferences(@RequestHeader("X-User-Id") Long userId) {
        return preferenceService.getPreferences(userId);
    }

    @PutMapping
    public void updatePreferences(@RequestHeader("X-User-Id") Long userId,
                                  @RequestBody List<NotificationPreference> preferences) {
        preferenceService.updatePreferences(userId, preferences);
    }
}