package com.bank.template.controller;

import com.bank.common.entity.NotificationTemplate;
import com.bank.template.service.TemplateService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/templates")
public class TemplateController {
    @Autowired
    private TemplateService templateService;

    @GetMapping
    public List<NotificationTemplate> list(@RequestParam(required = false) String eventType,
                                           @RequestParam(required = false) String channel,
                                           @RequestParam(required = false) String locale) {
        return templateService.findAll(eventType, channel, locale);
    }

    @PostMapping
    public NotificationTemplate create(@RequestBody NotificationTemplate template) {
        return templateService.create(template);
    }

    @PutMapping("/{id}")
    public NotificationTemplate update(@PathVariable Long id, @RequestBody NotificationTemplate template) {
        template.setId(id);
        return templateService.update(template);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        templateService.delete(id);
    }
}