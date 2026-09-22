package com.bank.channel.service;

/**
 * 渠道侧的"带原因码的失败"。
 *
 * <p>为什么需要它：发送方法上的 {@code @CircuitBreaker(fallbackMethod = "sendFallback")} 只把
 * {@code Throwable} 传给 fallback，而 fallback 才写状态回调。用异常消息传原因码就得在
 * fallback 里反向解析中文文案 —— 那是必然腐坏的耦合。
 */
public class ProviderRejectException extends RuntimeException {

    private final String reasonCode;

    /**
     * 已经组装好的请求体（TemplateParam JSON）。
     * <p>带上它是因为重投发生在<b>失败之后</b>：如果只在成功时落 {@code sent_logs.request}，
     * 失败行的请求体永远是 NULL，重投就恢复不出多变量模板的变量，这个复用机制等于没有。
     */
    private String request;

    public ProviderRejectException(String reasonCode, String message) {
        super(message);
        this.reasonCode = reasonCode;
    }

    public ProviderRejectException(String reasonCode, String message, Throwable cause) {
        super(message, cause);
        this.reasonCode = reasonCode;
    }

    public ProviderRejectException withRequest(String request) {
        this.request = request;
        return this;
    }

    public String getReasonCode() {
        return reasonCode;
    }

    public String getRequest() {
        return request;
    }
}
