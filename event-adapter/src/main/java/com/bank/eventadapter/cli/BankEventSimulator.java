package com.bank.eventadapter.cli;

import com.bank.common.constant.AppConstants;
import com.bank.common.dto.BankEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Scanner;
import java.util.UUID;

/**
 * 银行事件模拟器 - 命令行工具，用于手动触发银行事件发送到 Kafka
 */
@Slf4j
public class BankEventSimulator {

    private static final String KAFKA_BROKER = "localhost:9092";
    private static final ObjectMapper objectMapper = new ObjectMapper();

    public static void main(String[] args) {
        log.info("=== 银行事件模拟器启动 ===");
        
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA_BROKER);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class.getName());
        props.put("spring.json.trusted.packages", "*");

        try (KafkaProducer<String, Object> producer = new KafkaProducer<>(props);
             Scanner scanner = new Scanner(System.in)) {

            while (true) {
                System.out.println("\n=== 选择事件类型 ===");
                System.out.println("1. 交易事件 (TRANSACTION)");
                System.out.println("2. 风险预警事件 (RISK_ALERT)");
                System.out.println("3. 账单事件 (BILL)");
                System.out.println("4. 自定义事件");
                System.out.println("5. 批量发送测试");
                System.out.println("0. 退出");
                System.out.print("请选择: ");

                int choice = scanner.nextInt();
                scanner.nextLine(); // 消费换行符

                switch (choice) {
                    case 1 -> sendTransactionEvent(producer);
                    case 2 -> sendRiskAlertEvent(producer);
                    case 3 -> sendBillEvent(producer);
                    case 4 -> sendCustomEvent(producer, scanner);
                    case 5 -> sendBatchEvents(producer);
                    case 0 -> {
                        log.info("=== 退出事件模拟器 ===");
                        return;
                    }
                    default -> System.out.println("无效选择，请重新输入");
                }
            }

        } catch (Exception e) {
            log.error("事件发送失败", e);
        }
    }

    private static void sendTransactionEvent(KafkaProducer<String, Object> producer) {
        BankEvent event = new BankEvent();
        event.setEventId("evt-trans-" + UUID.randomUUID());
        event.setEventType("TRANSACTION");
        event.setCustomerId(1L);
        
        Map<String, Object> payload = new HashMap<>();
        payload.put("account", "1234567890");
        payload.put("amount", 1000.50);
        payload.put("currency", "RUB");
        payload.put("transactionType", "DEBIT");
        payload.put("description", "POS消费");
        payload.put("merchant", "超市购物");
        event.setPayload(payload);
        event.setTimestamp(LocalDateTime.now());

        sendEvent(producer, event);
    }

    private static void sendRiskAlertEvent(KafkaProducer<String, Object> producer) {
        BankEvent event = new BankEvent();
        event.setEventId("evt-risk-" + UUID.randomUUID());
        event.setEventType("RISK_ALERT");
        event.setCustomerId(2L);
        
        Map<String, Object> payload = new HashMap<>();
        payload.put("account", "1234567890");
        payload.put("riskLevel", "HIGH");
        payload.put("riskType", "异地登录");
        payload.put("ipAddress", "192.168.1.100");
        payload.put("location", "北京市");
        payload.put("device", "iPhone 15");
        event.setPayload(payload);
        event.setTimestamp(LocalDateTime.now());

        sendEvent(producer, event);
    }

    private static void sendBillEvent(KafkaProducer<String, Object> producer) {
        BankEvent event = new BankEvent();
        event.setEventId("evt-bill-" + UUID.randomUUID());
        event.setEventType("BILL");
        event.setCustomerId(3L);
        
        Map<String, Object> payload = new HashMap<>();
        payload.put("billType", "信用卡还款");
        payload.put("amount", 5000.00);
        payload.put("dueDate", "2024-02-15");
        payload.put("account", "****6789");
        event.setPayload(payload);
        event.setTimestamp(LocalDateTime.now());

        sendEvent(producer, event);
    }

    private static void sendCustomEvent(KafkaProducer<String, Object> producer, Scanner scanner) {
        System.out.print("输入事件类型: ");
        String eventType = scanner.nextLine();
        
        System.out.print("输入客户ID: ");
        Long customerId = scanner.nextLong();
        scanner.nextLine();

        BankEvent event = new BankEvent();
        event.setEventId("evt-custom-" + UUID.randomUUID());
        event.setEventType(eventType);
        event.setCustomerId(customerId);
        
        Map<String, Object> payload = new HashMap<>();
        payload.put("customField", "自定义数据");
        payload.put("source", "命令行工具");
        event.setPayload(payload);
        event.setTimestamp(LocalDateTime.now());

        sendEvent(producer, event);
    }

    private static void sendBatchEvents(KafkaProducer<String, Object> producer) {
        System.out.print("输入批量发送数量: ");
        Scanner scanner = new Scanner(System.in);
        int count = scanner.nextInt();
        
        for (int i = 0; i < count; i++) {
            String eventType = switch (i % 3) {
                case 0 -> "TRANSACTION";
                case 1 -> "RISK_ALERT";
                default -> "BILL";
            };
            
            BankEvent event = new BankEvent();
            event.setEventId("evt-batch-" + i + "-" + UUID.randomUUID());
            event.setEventType(eventType);
            event.setCustomerId((long) (i % 10 + 1));
            
            Map<String, Object> payload = new HashMap<>();
            payload.put("batchIndex", i);
            payload.put("totalCount", count);
            event.setPayload(payload);
            event.setTimestamp(LocalDateTime.now());

            sendEvent(producer, event);
            
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        
        log.info("批量发送完成，共发送 {} 条事件", count);
    }

    private static void sendEvent(KafkaProducer<String, Object> producer, BankEvent event) {
        try {
            ProducerRecord<String, Object> record = new ProducerRecord<>(
                AppConstants.TOPIC_BANK_EVENTS,
                event.getEventId(),
                event
            );
            
            producer.send(record, (metadata, exception) -> {
                if (exception == null) {
                    log.info("事件发送成功!");
                    log.info("  Event ID: {}", event.getEventId());
                    log.info("  Event Type: {}", event.getEventType());
                    log.info("  Customer ID: {}", event.getCustomerId());
                    log.info("  Topic: {}", metadata.topic());
                    log.info("  Partition: {}", metadata.partition());
                    log.info("  Offset: {}", metadata.offset());
                } else {
                    log.error("事件发送失败: {}", exception.getMessage());
                }
            });
            
            producer.flush();
            
        } catch (Exception e) {
            log.error("发送事件时发生错误", e);
        }
    }
}
