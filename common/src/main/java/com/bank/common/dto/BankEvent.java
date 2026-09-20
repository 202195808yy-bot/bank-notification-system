package com.bank.common.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 银行事件对象，通过 Kafka 传输
 */
@Data
public class BankEvent {

    private String eventId;
    private String eventType;
    private Long customerId;
    private Map<String, Object> payload;
    private LocalDateTime timestamp;
}