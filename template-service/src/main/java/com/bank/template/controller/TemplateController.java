package com.bank.template.controller;

import com.bank.common.entity.NotificationTemplate;
import com.bank.template.exception.TemplateException;
import com.bank.template.service.TemplateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/templates")
@RequiredArgsConstructor
public class TemplateController {

    private final TemplateService templateService;

    /**
     * 列表/按维度筛选。三个参数都可只传一部分（原先走 *And* 派生查询，
     * 少传一个条件就等于查不到，前端模板页的筛选因此一直是死的）。
     */
    @GetMapping
    public List<NotificationTemplate> list(@RequestParam(required = false) String eventType,
                                           @RequestParam(required = false) String channel,
                                           @RequestParam(required = false) String locale) {
        return templateService.findAll(eventType, channel, locale);
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody NotificationTemplate template) {
        try {
            return ResponseEntity.ok(templateService.create(template));
        } catch (TemplateException e) {
            return error(e);
        } catch (DataIntegrityViolationException e) {
            // 唯一约束 (event_type, channel, locale) 兜底并发写入
            log.warn("模板创建撞唯一键: {}", template, e);
            return error(new TemplateException(HttpStatus.CONFLICT, "TEMPLATE_EXISTS", "同一 事件类型/渠道/语言 的模板已存在"));
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable Long id, @RequestBody NotificationTemplate template) {
        try {
            return ResponseEntity.ok(templateService.update(id, template));
        } catch (TemplateException e) {
            return error(e);
        } catch (DataIntegrityViolationException e) {
            log.warn("模板更新撞唯一键: id={}", id, e);
            return error(new TemplateException(HttpStatus.CONFLICT, "TEMPLATE_CONFLICT", "另一条模板已占用该 事件类型/渠道/语言"));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        try {
            templateService.delete(id);
            return ResponseEntity.ok().build();
        } catch (TemplateException e) {
            return error(e);
        }
    }

    private ResponseEntity<Map<String, String>> error(TemplateException e) {
        return ResponseEntity.status(e.getStatus()).body(Map.of("code", e.getCode(), "message", e.getMessage()));
    }
}
