package com.bank.customer.repository;

import com.bank.common.entity.NotificationPreference;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PreferenceRepository extends JpaRepository<NotificationPreference, Long> {

    /**
     * 根据客户 ID 查询偏好列表
     */
    List<NotificationPreference> findByCustomerId(Long customerId);

    /**
     * 按事件类型查全库订阅者，供管理员的送达面预检使用（PRD-41）
     */
    List<NotificationPreference> findByEventType(String eventType);

    /**
     * 根据客户 ID 删除所有偏好
     */
    void deleteByCustomerId(Long customerId);
}