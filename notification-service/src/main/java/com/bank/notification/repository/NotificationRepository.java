package com.bank.notification.repository;

import com.bank.common.entity.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long>, JpaSpecificationExecutor<Notification> {
    Page<Notification> findByCustomerId(Long customerId, Pageable pageable);

    @Query("SELECT n.status, COUNT(n) FROM Notification n WHERE n.customerId = :customerId GROUP BY n.status")
    List<Object[]> countByCustomerIdGroupByStatus(@Param("customerId") Long customerId);

    @Query("SELECT n.status, COUNT(n) FROM Notification n GROUP BY n.status")
    List<Object[]> countAllGroupByStatus();

    long countByEventId(String eventId);

    /**
     * 未读数只统计真正投递过的通知。
     * SKIPPED / FAILED_VALIDATION 是客户从未收到的记录，算进红点会误导。
     */
    @Query("SELECT COUNT(n) FROM Notification n WHERE n.customerId = :customerId AND n.read = false "
            + "AND n.status IN (com.bank.common.enums.SendStatus.PENDING, "
            + "com.bank.common.enums.SendStatus.SENT, com.bank.common.enums.SendStatus.FAILED)")
    long countUnreadDelivered(@Param("customerId") Long customerId);

    @Modifying
    @Query("UPDATE Notification n SET n.read = true, n.updatedAt = CURRENT_TIMESTAMP "
            + "WHERE n.customerId = :customerId AND n.read = false "
            + "AND n.status IN (com.bank.common.enums.SendStatus.PENDING, "
            + "com.bank.common.enums.SendStatus.SENT, com.bank.common.enums.SendStatus.FAILED)")
    int markAllReadForCustomer(@Param("customerId") Long customerId);
}
