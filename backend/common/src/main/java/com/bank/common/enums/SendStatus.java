package com.bank.common.enums;

/**
 * 通知发送状态。
 * <p>
 * 前三者是投递结果；SKIPPED / FAILED_VALIDATION 表示"根本没有交给渠道"，
 * 之所以也要落库，是为了让运维能区分「客户不想收」和「系统没发出去」
 * （修复前所有跳过分支都静默 return，一条记录都不留）。
 */
public enum SendStatus {
    PENDING,            // 待发送
    SENT,               // 已发送
    FAILED,             // 发送失败
    SKIPPED,            // 按规则未发送（免打扰时段、模板缺失等），见 reason
    FAILED_VALIDATION   // 数据不完整无法发送（如客户缺少该渠道的联系方式）
}