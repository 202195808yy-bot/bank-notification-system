package com.bank.customer.controller;

import com.bank.common.dto.CustomerContact;
import com.bank.customer.service.CustomerProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 服务间接口：按客户 ID 取收件地址，供 notification-service 派发时调用。
 * <p>
 * 刻意不放在 /api 下：网关没有匹配 {@code /internal/**} 的路由谓词，
 * 因此外部请求根本到不了这里，只有容器网络内带内部令牌的调用能访问（见 InternalApiAuthFilter）。
 */
@RestController
@RequestMapping("/internal/customers")
@RequiredArgsConstructor
public class InternalCustomerController {

    private final CustomerProfileService profileService;

    @GetMapping("/{id}/contact")
    public CustomerContact contact(@PathVariable Long id) {
        return profileService.getContact(id);
    }
}
