package com.bank.customer.controller;

import com.bank.common.constant.AppConstants;
import com.bank.common.entity.NotificationPreference;
import com.bank.customer.security.CurrentUserResolver;
import com.bank.customer.service.PreferenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/preferences")
@RequiredArgsConstructor
public class PreferenceController {

    private final PreferenceService preferenceService;
    private final CurrentUserResolver currentUserResolver;

    /**
     * 查询当前用户的通知偏好
     */
    @GetMapping
    public ResponseEntity<?> getPreferences(HttpServletRequest request) {
        Long customerId = requireCustomerId(request);
        if (customerId == null) {
            return unauthorized();
        }
        return ResponseEntity.ok(preferenceService.getPreferences(customerId));
    }

    /**
     * 全量更新当前用户的通知偏好。
     * 响应带 warnings（PRD-40）：订阅了但客户缺该渠道收件地址时，保存仍然成功，
     * 只是当场给出「这个渠道现在送不到」的提示 —— 不阻断，因为补地址本来就是后续动作。
     */
    @PutMapping
    public ResponseEntity<?> updatePreferences(@RequestBody List<NotificationPreference> preferences,
                                               HttpServletRequest request) {
        Long customerId = requireCustomerId(request);
        if (customerId == null) {
            return unauthorized();
        }
        try {
            var warnings = preferenceService.updatePreferences(customerId, preferences);
            return ResponseEntity.ok(Map.of("warnings", warnings));
        } catch (DataIntegrityViolationException e) {
            // 唯一键冲突（同一 (customer_id, event_type) 重复）
            log.warn("偏好更新冲突: customerId={}", customerId, e);
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("message", "数据冲突：同一事件类型的偏好重复"));
        } catch (IllegalArgumentException e) {
            // 服务层用异常消息当错误码（与 CustomerController 同一套约定）；
            // 同时给 message，未收录进语料的码还能看到原文
            return ResponseEntity.badRequest().body(Map.of("code", e.getMessage(), "message", e.getMessage()));
        }
    }

    /**
     * 某个事件类型的可送达面（谁订阅了 / 哪些渠道缺地址），管理员在发送事件之前看
     * （PRD-41）。这是全库数据，因此 ADMIN-only；网关侧另有一道 ADMIN_PREFIX。
     */
    @GetMapping("/reach")
    public ResponseEntity<?> reachability(@RequestParam String eventType,
                                          @RequestHeader(value = "X-User-Role", required = false) String role,
                                          HttpServletRequest request) {
        if (requireCustomerId(request) == null) {
            return unauthorized();
        }
        if (!AppConstants.ROLE_ADMIN.equals(role)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("code", "ADMIN_REQUIRED"));
        }
        return ResponseEntity.ok(preferenceService.reachability(eventType));
    }

    private Long requireCustomerId(HttpServletRequest request) {
        return currentUserResolver.resolve(request);
    }

    private ResponseEntity<Object> unauthorized() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "未认证"));
    }
}
