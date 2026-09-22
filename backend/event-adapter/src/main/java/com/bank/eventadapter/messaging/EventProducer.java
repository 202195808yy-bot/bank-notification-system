package com.bank.eventadapter.messaging;

import com.bank.common.constant.AppConstants;
import com.bank.common.dto.BankEvent;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Component
@RequiredArgsConstructor
public class EventProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    /**
     * 同步确认的上限。必须明显小于前端与网关的请求超时，否则"队列不可用"会先表现为一次长挂起
     * （修前就是：send() 立即返回，调用方只看到 200）。
     */
    @Value("${event.publish-confirm-timeout-ms:8000}")
    private long confirmTimeoutMs;

    /**
     * 投递并**等 broker 确认**为止。
     *
     * 修前这里只是 {@code kafkaTemplate.send(...)} 不取结果：200「事件已发布」代表的其实是
     * "消息进了生产者本地缓冲区"。2026-09-21 14:33–14:36 的实测里 Kafka 不可用，缓冲区在
     * {@code delivery.timeout.ms≈120s} 后整批丢弃（日志 {@code Expiring 10 record(s) for bank.events-0}），
     * 那 10 个事件在 notifications 里连一行都没有，而界面全程显示成功。
     *
     * @return broker 返回的 partition/offset，供响应体回显
     * @throws IllegalStateException 带错误码 {@code EVENT_PUBLISH_FAILED}，由控制器映射成 5xx
     */
    public RecordMetadata publish(BankEvent event) {
        try {
            SendResult<String, Object> result = kafkaTemplate
                    .send(AppConstants.TOPIC_BANK_EVENTS, event.getEventId(), event)
                    .get(confirmTimeoutMs, TimeUnit.MILLISECONDS);
            return result.getRecordMetadata();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("EVENT_PUBLISH_INTERRUPTED", e);
        } catch (TimeoutException | ExecutionException e) {
            throw new IllegalStateException("EVENT_PUBLISH_FAILED", e);
        }
    }
}
