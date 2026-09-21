package com.bank.notification.config;

import com.bank.common.constant.AppConstants;
import com.bank.common.dto.BankEvent;
import com.bank.common.dto.NotificationStatus;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.boot.autoconfigure.kafka.ConcurrentKafkaListenerContainerFactoryConfigurer;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.util.backoff.FixedBackOff;

import java.util.Map;

/**
 * PRD-42：以前一条解析不了的消息就能把整个分区钉死 —— 反序列化异常发生在进入业务代码之前，
 * offset 永远提交不了，而事件接口照样返回 200。现在脏记录重试后落到 &lt;topic&gt;.DLT，分区继续前进。
 */
@Configuration
public class KafkaErrorHandlingConfig {

    /** 重试 2 次（间隔 1 秒）后放弃并转 DLT；再多的重试只会让毒丸消息拖慢整个分区。 */
    private static final FixedBackOff RETRY = new FixedBackOff(1_000L, 2L);

    @Bean
    public NewTopic bankEventsDltTopic() {
        return dltTopic(AppConstants.TOPIC_BANK_EVENTS_DLT);
    }

    @Bean
    public NewTopic statusDltTopic() {
        return dltTopic(AppConstants.TOPIC_STATUS_DLT);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<Object, Object> bankEventsListenerContainerFactory(
            ConcurrentKafkaListenerContainerFactoryConfigurer configurer,
            KafkaProperties kafkaProperties) {
        return containerFactory(configurer, kafkaProperties, BankEvent.class);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<Object, Object> statusListenerContainerFactory(
            ConcurrentKafkaListenerContainerFactoryConfigurer configurer,
            KafkaProperties kafkaProperties) {
        return containerFactory(configurer, kafkaProperties, NotificationStatus.class);
    }

    private ConcurrentKafkaListenerContainerFactory<Object, Object> containerFactory(
            ConcurrentKafkaListenerContainerFactoryConfigurer configurer,
            KafkaProperties kafkaProperties,
            Class<?> payloadType) {

        Map<String, Object> consumerProperties = kafkaProperties.buildConsumerProperties();
        // 两个主题的负载类型不同，default.type 只能按主题分别注入，写不进 yml 里那段共用的 consumer 配置。
        consumerProperties.put(JsonDeserializer.VALUE_DEFAULT_TYPE, payloadType.getName());
        ConsumerFactory<Object, Object> consumerFactory = new DefaultKafkaConsumerFactory<>(consumerProperties);

        ConcurrentKafkaListenerContainerFactory<Object, Object> factory = new ConcurrentKafkaListenerContainerFactory<>();
        configurer.configure(factory, consumerFactory);
        factory.setCommonErrorHandler(new DefaultErrorHandler(recoverer(kafkaProperties), RETRY));
        return factory;
    }

    private DeadLetterPublishingRecoverer recoverer(KafkaProperties kafkaProperties) {
        KafkaOperations<Object, Object> rawBytes = rawBytesTemplate(kafkaProperties);
        KafkaOperations<Object, Object> json = jsonTemplate(kafkaProperties);
        // 反序列化失败的记录，负载是原始 byte[]；业务异常失败的记录，负载已经是 DTO 对象。
        // 只挂一种序列化器就会在其中一类记录上抛 SerializationException，而这个异常会把分区重新钉死。
        // 两套模板的键都用 StringSerializer：三个主题的键都是 notificationId/eventId 字符串，
        // 而 StringDeserializer 不会失败，键因此永远不会是原始字节；键也上 ByteArray 时，
        // 带键的脏记录会改在键上抛 SerializationException（同样实测踩过）。
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                producerRecord -> producerRecord.value() instanceof byte[] ? rawBytes : json,
                (record, exception) -> new TopicPartition(record.topic() + AppConstants.DLT_SUFFIX, record.partition()));
        // 投死信本身失败时只记 ERROR，绝不再抛异常：DefaultErrorHandler 拿不到结论会 seek 回原处，
        // 分区又被钉死 —— 那正是本次要修的缺陷。宁可丢一条进不了 DLT 的记录，也不能拖停整条通知链。
        recoverer.setFailIfSendResultIsError(false);
        return recoverer;
    }

    /** DLT 里要保留原始字节，所以值用 ByteArray 序列化器；键仍是 String（见 recoverer 的注释）。 */
    private KafkaOperations<Object, Object> rawBytesTemplate(KafkaProperties kafkaProperties) {
        Map<String, Object> producerProperties = kafkaProperties.buildProducerProperties();
        producerProperties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        producerProperties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class);
        return new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(producerProperties));
    }

    /** 业务异常时记录已经被反序列化成 DTO，只能按 JSON 投出去。 */
    private KafkaOperations<Object, Object> jsonTemplate(KafkaProperties kafkaProperties) {
        Map<String, Object> producerProperties = kafkaProperties.buildProducerProperties();
        producerProperties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        producerProperties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        return new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(producerProperties));
    }

    private NewTopic dltTopic(String name) {
        return TopicBuilder.name(name).partitions(1).replicas(1).build();
    }
}
