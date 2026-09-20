package com.bank.customer.service;

import com.bank.common.dto.CustomerContact;
import com.bank.common.entity.Customer;
import com.bank.customer.dto.ProfileRequest;
import com.bank.customer.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
        return contact;
    }

    /**
     * 更新联系方式。邮箱同时是登录名，因此要查重。
     */
    @Transactional
    public Customer updateProfile(Long customerId, ProfileRequest request) {
        String email = request.getEmail().trim();
        if (customerRepository.existsByEmailAndIdNot(email, customerId)) {
            throw new EmailAlreadyUsedException();
        }
        Customer customer = get(customerId);
        customer.setName(request.getName().trim());
        customer.setEmail(email);
        customer.setPhone(request.getPhone().trim());
        // PATCH 语义：字段缺省表示不修改，空串表示清除 push 令牌
        if (request.getPushToken() != null) {
            String token = request.getPushToken().trim();
            customer.setPushToken(token.isEmpty() ? null : token);
        }
        log.info("客户资料更新: customerId={}", customerId);
        return customerRepository.save(customer);
    }

    /** 邮箱已被占用：控制器据此返回 409 */
    public static class EmailAlreadyUsedException extends RuntimeException {
        public EmailAlreadyUsedException() {
            super("EMAIL_ALREADY_USED");
        }
    }
}
