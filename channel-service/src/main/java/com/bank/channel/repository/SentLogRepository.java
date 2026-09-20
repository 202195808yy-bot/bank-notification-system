package com.bank.channel.repository;

import com.bank.common.entity.SentLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SentLogRepository extends JpaRepository<SentLog, Long> {
}