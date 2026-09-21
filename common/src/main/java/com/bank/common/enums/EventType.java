package com.bank.common.enums;

/**
 * 银行事件类型：模板 CRUD 的白名单来源（template-service 据此拒绝未知类型）。
 * ⚠️ 必须与前端 web/src/utils/constants.js 的 EVENT_TYPES 逐项一致 —— 少一项，
 * 界面上下拉框能选、后端却会 400；多一项，模板永远匹配不到事件。
 */
public enum EventType {
    TRANSACTION,  // 交易通知
    RISK_ALERT,   // 风险提醒
    PROMOTION,    // 营销活动
    BILL,         // 账单通知
    LOGIN,        // 登录提醒
    SECURITY      // 安全提醒
}
