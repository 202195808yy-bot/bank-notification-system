package com.bank.customer.service;

import com.bank.common.constant.AppConstants;
import com.bank.common.dto.CustomerContact;
import com.bank.common.entity.Customer;
import com.bank.customer.dto.CustomerDirectoryEntry;
import com.bank.customer.dto.ProfileRequest;
import com.bank.customer.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CustomerProfileService {

    private final CustomerRepository customerRepository;

    public Customer get(Long customerId) {
        return customerRepository.findById(customerId)
                .orElseThrow(() -> new IllegalArgumentException("Клиент не найден"));
    }

    public CustomerContact getContact(Long customerId) {
        Customer customer = get(customerId);
        CustomerContact contact = new CustomerContact();
        contact.setEmail(customer.getEmail());
        contact.setPhone(customer.getPhone());
        contact.setPushToken(customer.getPushToken());
        contact.setLocale(customer.getLocale());
        contact.setTimezone(customer.getTimezone());
        return contact;
    }

    /**
     * 更新联系方式。PATCH 语义：null = 不修改；name/email 传空串拒绝（name 列 NOT NULL，
     * email 同时是登录名，清空等于把账号锁死）；phone / pushToken 传空串 = 清除，
     * 对应渠道之后会各自落 NO_PHONE / NO_PUSH_TOKEN。邮箱是登录名，改动要查重。
     */
    @Transactional
    public Customer updateProfile(Long customerId, ProfileRequest request) {
        Customer customer = get(customerId);
        boolean touched = false;

        if (request.getName() != null) {
            String name = request.getName().trim();
            if (name.isEmpty()) {
                throw new IllegalArgumentException("NAME_REQUIRED");
            }
            customer.setName(name);
            touched = true;
        }
        if (request.getEmail() != null) {
            String email = request.getEmail().trim();
            if (email.isEmpty()) {
                throw new IllegalArgumentException("EMAIL_REQUIRED");
            }
            if (customerRepository.existsByEmailAndIdNot(email, customerId)) {
                throw new EmailAlreadyUsedException();
            }
            customer.setEmail(email);
            touched = true;
        }
        if (request.getPhone() != null) {
            String phone = request.getPhone().trim();
            customer.setPhone(phone.isEmpty() ? null : phone);
            touched = true;
        }
        if (request.getPushToken() != null) {
            String token = request.getPushToken().trim();
            customer.setPushToken(token.isEmpty() ? null : token);
            touched = true;
        }
        if (request.getLocale() != null) {
            String locale = request.getLocale().trim();
            // 空串 = 恢复默认语言；其它值必须在白名单内，否则会存成一个永远匹配不到模板的 locale，
            // 表现为该客户所有渠道 SKIPPED/TEMPLATE_MISSING（派发端只对 null 做回退）
            if (locale.isEmpty()) {
                customer.setLocale(AppConstants.DEFAULT_LOCALE);
            } else if (!AppConstants.SUPPORTED_LOCALES.contains(locale)) {
                throw new IllegalArgumentException("UNSUPPORTED_LOCALE");
            } else {
                customer.setLocale(locale);
            }
            touched = true;
        }
        if (request.getTimezone() != null) {
            // 空串 = 清除，派发端回退 app.business-zone；非法 IANA 名一律 400，不能存一个悄悄失效的值
            customer.setTimezone(com.bank.customer.util.Timezones.normalize(request.getTimezone()));
            touched = true;
        }
        if (!touched) {
            throw new IllegalArgumentException("NOTHING_TO_UPDATE");
        }
        log.info("客户资料更新: customerId={}", customerId);
        return customerRepository.save(customer);
    }

    /**
     * 管理员用的用户目录。此前后端对管理员能列出的客户数为零，
     * 于是事件模拟器只能硬编码「用户 1..10」，其中大部分 id 并不存在（PRD-33 的孤儿行来源）。
     */
    public List<CustomerDirectoryEntry> directory() {
        return customerRepository.findAll(Sort.by(Sort.Direction.ASC, "id")).stream()
                .map(c -> new CustomerDirectoryEntry(
                        c.getId(),
                        c.getName(),
                        c.getEmail(),
                        c.getRole() != null ? c.getRole() : AppConstants.DEFAULT_ROLE,
                        isFilled(c.getEmail()),
                        isFilled(c.getPhone()),
                        isFilled(c.getPushToken())))
                .toList();
    }

    private static boolean isFilled(String value) {
        return value != null && !value.isBlank();
    }

    /** 邮箱已被占用：控制器据此返回 409 */
    public static class EmailAlreadyUsedException extends RuntimeException {
        public EmailAlreadyUsedException() {
            super("EMAIL_ALREADY_USED");
        }
    }
}
