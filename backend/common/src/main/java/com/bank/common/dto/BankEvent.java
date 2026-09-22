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

    /**
     * 管理员在事件发送页手填的收件地址（手机号或邮箱）。非空时这一次派发**不查偏好、不查客户档案、
     * 不判免打扰**，直接把渲染好的正文发到这个地址上。
     */
    private String directRecipient;

    /** directRecipient 的渠道（SMS / EMAIL），由入口按地址形状判定后随事件传递 */
    private String directChannel;

    /** 直发时正文用哪本语料的模板；缺省回退 {@code AppConstants.DEFAULT_LOCALE} */
    private String directLocale;
}