package com.bank.notification.repository;

import com.bank.common.entity.Notification;
import com.bank.common.enums.SendStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
    Page<Notification> findByCustomerId(Long customeёrId, Pageable pageable);
    long countByCustomerId(Long customerId);
    long countByCustomerIdAndStatus(Long customerId, SendStatus status);

}