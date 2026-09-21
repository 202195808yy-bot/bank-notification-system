package com.bank.common.dto;

import lombok.Data;

/**
 * 发送命令，由 notification-service 发往 channel-service
 */
@Data
public class SendCommand {

    private Long notificationId;
    private Long customerId;
    private String channel;
    private String recipient;   // 手机号/邮箱/推送令牌
    private String content;
    /** 已渲染的标题，仅邮件通道用作主题；其它通道忽略 */
    private String subject;
    private Long templateId;
}