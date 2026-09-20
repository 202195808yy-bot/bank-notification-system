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
    private Long templateId;
}