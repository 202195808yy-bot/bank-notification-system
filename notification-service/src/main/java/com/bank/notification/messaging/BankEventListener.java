package com.bank.notification.messaging;

import com.bank.common.constant.AppConstants;
import com.bank.common.dto.BankEvent;
import com.bank.notification.service.NotificationDispatchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class BankEventListener {

    private final NotificationDispatchService dispatchService;

    @KafkaListener(topics = AppConstants.TOPIC_BANK_EVENTS,
            groupId = "notification-service",
            containerFactory = "bankEventsListenerContainerFactory")
    public void onEvent(BankEvent event) {
        if (event == null) {
            // 反序列化失败时 ErrorHandlingDeserializer 返回 null，原始字节和异常都在消息头里。
            // 抛出去是为了让 DefaultErrorHandler 记录并重试，最终投进 bank.events.DLT，而不是停在这里。
            throw new IllegalStateException("银行事件反序列化失败，转入 " + AppConstants.TOPIC_BANK_EVENTS_DLT);
        }
        log.info("收到银行事件: eventId={}, eventType={}, customerId={}",
                event.getEventId(), event.getEventType(), event.getCustomerId());
        dispatchService.dispatch(event);
    }
}