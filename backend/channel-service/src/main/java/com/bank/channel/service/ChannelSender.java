package com.bank.channel.service;

import com.bank.common.dto.SendCommand;

public interface ChannelSender {
    void send(SendCommand command);

    /**
     * 网络层的异常常常根本没有 message（JDK HttpClient 解析域名失败抛的
     * UnresolvedAddressException 就是空的），只拼 {@code e.getMessage()} 会让
     * sent_logs.response 写成「发送失败: null」，运维无从区分没网、域名错还是网关拒绝。
     */
    static String describe(Throwable e) {
        Throwable root = e;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        String msg = root.getMessage();
        return (msg == null || msg.isBlank())
                ? root.getClass().getSimpleName()
                : root.getClass().getSimpleName() + ": " + msg;
    }
}