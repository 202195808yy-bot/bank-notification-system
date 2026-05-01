package com.bank.channel.controller;

import com.bank.common.dto.NotificationStatus;
import com.bank.channel.messaging.StatusProducer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/callback")
public class CallbackController {
    @Autowired
    private StatusProducer statusProducer;

    @PostMapping("/{channel}/{provider}")
    public void handleCallback(@PathVariable String channel,
                               @PathVariable String provider,
                               @RequestParam Long notificationId,
                               @RequestParam String status,
                               @RequestParam(required = false) String response) {
        NotificationStatus ns = new NotificationStatus();
        ns.setNotificationId(notificationId);
        ns.setStatus(status);
        ns.setProvider(provider);
        ns.setProviderResponse(response);
        ns.setTimestamp(LocalDateTime.now());
        statusProducer.send(ns);
    }
}