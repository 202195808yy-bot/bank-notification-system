package com.bank.notification.client;

import com.bank.common.dto.CustomerContact;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "customer-service-contact", url = "${customer.service.url:http://customer-service:8081}")
public interface ContactClient {

    /**
     * 取客户在各渠道上的收件地址。内部路径，不经网关暴露。
     */
    @GetMapping("/internal/customers/{id}/contact")
    CustomerContact getContact(@PathVariable("id") Long customerId);
}
