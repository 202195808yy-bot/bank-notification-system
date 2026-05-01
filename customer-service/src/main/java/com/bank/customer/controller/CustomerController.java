package com.bank.customer.controller;

import com.bank.common.entity.Customer;
import com.bank.customer.repository.CustomerRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/customers")
public class CustomerController {
    @Autowired
    private CustomerRepository customerRepository;

    @GetMapping("/me")
    public Customer getCurrentCustomer(@RequestHeader("X-User-Id") Long userId) {
        return customerRepository.findById(userId).orElse(null);
    }
}