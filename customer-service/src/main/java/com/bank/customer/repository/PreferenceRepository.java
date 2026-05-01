package com.bank.customer.repository;

import com.bank.common.entity.NotificationPreference;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PreferenceRepository extends JpaRepository<NotificationPreference, Long> {
    List<NotificationPreference> findByCustomerId(Long customerId);
    void deleteByCustomerId(Long customerId);
}