package com.bank.customer.repository;

import com.bank.common.entity.Customer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CustomerRepository extends JpaRepository<Customer, Long> {

    /**
     * 根据邮箱查询客户
     */
    Optional<Customer> findByEmail(String email);

    /**
     * 邮箱是否被其他客户占用（改资料时查重）
     */
    boolean existsByEmailAndIdNot(String email, Long id);
}