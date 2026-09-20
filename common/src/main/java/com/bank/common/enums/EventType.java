package com.bank.common.enums;

/**
 * 银行事件类型
 */
public enum EventType {
    TRANSACTION,  // 交易通知
    RISK_ALERT,   // 风险提醒
    PROMOTION,    // 营销活动
    BILL          // 账单通知
}