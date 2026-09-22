package com.bank.customer.service;

import com.bank.common.constant.AppConstants;
import com.bank.common.dto.CustomerContact;
import com.bank.common.entity.Customer;
import com.bank.common.entity.NotificationPreference;
import com.bank.customer.dto.EventReachability;
import com.bank.customer.dto.PreferenceWarning;
import com.bank.customer.repository.CustomerRepository;
import com.bank.customer.repository.PreferenceRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PreferenceService {

    private final PreferenceRepository preferenceRepository;
    private final CustomerRepository customerRepository;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public List<NotificationPreference> getPreferences(Long customerId) {
        String cacheKey = AppConstants.PREF_CACHE_PREFIX + customerId;
        try {
            Map<Object, Object> entries = redisTemplate.opsForHash().entries(cacheKey);
            if (!entries.isEmpty()) {
                return entries.values().stream()
                        .map(v -> {
                            try {
                                return objectMapper.readValue((String) v, NotificationPreference.class);
                            } catch (JsonProcessingException e) {
                                log.warn("Cache deserialization failed", e);
                                return null;
                            }
                        })
                        .filter(Objects::nonNull)
                        .collect(Collectors.toList());
            }
        } catch (Exception e) {
            log.warn("Redis unavailable, falling back to database", e);
        }

        List<NotificationPreference> prefs = preferenceRepository.findByCustomerId(customerId);
        try {
            Map<String, String> map = new HashMap<>();
            for (NotificationPreference p : prefs) {
                map.put(p.getEventType(), objectMapper.writeValueAsString(p));
            }
            if (!map.isEmpty()) {
                redisTemplate.opsForHash().putAll(cacheKey, map);
            }
        } catch (Exception e) {
            log.warn("Failed to cache preferences", e);
        }
        return prefs;
    }

    @Transactional
    public List<PreferenceWarning> updatePreferences(Long customerId, List<NotificationPreference> newPrefs) {
        // 1. 按事件类型去重（同一客户同一事件只保留最后一条）
        Map<String, NotificationPreference> unique = new LinkedHashMap<>();
        for (NotificationPreference p : newPrefs) {
            p.setId(null);
            p.setCustomerId(customerId);
            unique.put(p.getEventType(), p);
        }
        List<NotificationPreference> finalList = new ArrayList<>(unique.values());

        // 0. 先校验再删旧行：不合法的免打扰时段根本不该落库（见 validateQuietPeriod）
        finalList.forEach(this::validateQuietPeriod);

        // 2. 删除该客户所有旧偏好
        preferenceRepository.deleteByCustomerId(customerId);
        preferenceRepository.flush();

        // 3. 逐条保存，内部冲突自动跳过
        for (NotificationPreference pref : finalList) {
            try {
                preferenceRepository.saveAndFlush(pref);
            } catch (DataIntegrityViolationException e) {
                log.warn("Duplicate preference ignored: customerId={}, eventType={}", customerId, pref.getEventType());
            }
        }

        // 4. 清除缓存
        try {
            redisTemplate.delete(AppConstants.PREF_CACHE_PREFIX + customerId);
        } catch (Exception e) {
            log.warn("Failed to clear cache", e);
        }

        return collectWarnings(customerId, finalList);
    }

    /**
     * 免打扰时段要么两端都填、要么都不填，且起止不能相同。
     * 只填一端时派发端按「未设置」处理（isQuietTime 直接 false），start==end 的区间则任何时刻
     * 都判不出「正在免打扰」—— 两种都是客户看不出来的失效设置：界面上填过了，实际永远不生效。
     */
    private void validateQuietPeriod(NotificationPreference pref) {
        boolean hasStart = pref.getQuietStart() != null;
        boolean hasEnd = pref.getQuietEnd() != null;
        if (hasStart ^ hasEnd) {
            throw new IllegalArgumentException("QUIET_PERIOD_INCOMPLETE");
        }
        if (hasStart && pref.getQuietStart().equals(pref.getQuietEnd())) {
            throw new IllegalArgumentException("QUIET_PERIOD_EMPTY");
        }
    }

    /**
     * 渠道 → 必需收件地址的可达性预检（PRD-40）。缺地址**不阻断保存**：登记手机号/邮箱/推送令牌
     * 本来就是可选动作，把它做成 400 会堵死"先订上、稍后再补地址"的正常流程。
     * 但也不能像以前那样直到派发完才在历史页露出来 —— 所以在保存这一刻就把警告返回给界面。
     * 用的码与 NotificationDispatchService 落库的 reason 同源（CustomerContact.missingReasonFor）。
     */
    private List<PreferenceWarning> collectWarnings(Long customerId, List<NotificationPreference> prefs) {
        Customer customer = customerRepository.findById(customerId).orElse(null);
        if (customer == null) {
            return List.of();
        }
        CustomerContact contact = toContact(customer);
        List<PreferenceWarning> warnings = new ArrayList<>();
        for (NotificationPreference pref : prefs) {
            if (!pref.isEnabled() || pref.getChannels() == null) {
                continue;
            }
            for (String channel : pref.getChannels()) {
                String reason = contact.missingReasonFor(channel);
                if (reason != null) {
                    warnings.add(new PreferenceWarning(pref.getId(), pref.getEventType(), channel, reason));
                }
            }
        }
        if (!warnings.isEmpty()) {
            log.info("偏好保存后存在不可达渠道: customerId={}, warnings={}", customerId, warnings.size());
        }
        return warnings;
    }

    /**
     * 某个事件类型在全库客户中的可送达面，供管理员在**发送之前**看到"有几个人真会收到"（PRD-41）。
     * 此前这些信息只有派发完、在历史页读 NOT_SUBSCRIBED / PREFERENCE_DISABLED / NO_* 才看得出来。
     */
    @Transactional(readOnly = true)
    public EventReachability reachability(String eventType) {
        Map<Long, NotificationPreference> byCustomer = preferenceRepository.findByEventType(eventType).stream()
                .collect(Collectors.toMap(NotificationPreference::getCustomerId, p -> p, (a, b) -> b));
        List<EventReachability.CustomerReach> rows = new ArrayList<>();
        int deliverable = 0;
        for (Customer customer : customerRepository.findAll(Sort.by(Sort.Direction.ASC, "id"))) {
            NotificationPreference pref = byCustomer.get(customer.getId());
            if (pref == null) {
                rows.add(new EventReachability.CustomerReach(
                        customer.getId(), customer.getName(), false, false, List.of(), List.of()));
                continue;
            }
            CustomerContact contact = toContact(customer);
            List<String> channels = pref.getChannels() == null ? List.of() : pref.getChannels();
            List<String> unreachable = channels.stream()
                    .filter(c -> contact.missingReasonFor(c) != null)
                    .toList();
            boolean canDeliver = pref.isEnabled() && !channels.isEmpty() && channels.size() > unreachable.size();
            if (canDeliver) {
                deliverable++;
            }
            rows.add(new EventReachability.CustomerReach(
                    customer.getId(), customer.getName(), true, pref.isEnabled(), channels, unreachable));
        }
        return new EventReachability(eventType, rows.size(), deliverable, rows);
    }

    private static CustomerContact toContact(Customer customer) {
        CustomerContact contact = new CustomerContact();
        contact.setEmail(customer.getEmail());
        contact.setPhone(customer.getPhone());
        contact.setPushToken(customer.getPushToken());
        contact.setLocale(customer.getLocale());
        contact.setTimezone(customer.getTimezone());
        return contact;
    }
}