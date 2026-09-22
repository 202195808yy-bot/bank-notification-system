package com.bank.common.dto;

import lombok.Data;

import java.util.Map;

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
    /**
     * 事件类型。短信通道用它解析"这个类型对应阿里云哪个已审核模板"
     * （{@code sms.event-template.<eventType>}），所以重投时也必须带上。
     */
    private String eventType;
    /**
     * 原始事件载荷。阿里云的模板变量有硬长度限制（实测单变量 20 字），
     * 整段正文塞一个变量必然被 {@code isv.PARAM_LENGTH_LIMIT} 拒，
     * 只能按变量逐个传。重投路径拿不到它（{@code notifications} 不存载荷），
     * 由 channel-service 从上一次 {@code sent_logs.request} 复用。
     */
    private Map<String, Object> variables;
}