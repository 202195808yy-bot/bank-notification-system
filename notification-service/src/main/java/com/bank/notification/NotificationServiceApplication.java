package com.bank.notification;

import com.bank.common.config.InternalApiSecurityConfig;
import com.bank.common.config.JacksonConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EntityScan("com.bank.common.entity")
@EnableJpaRepositories("com.bank.notification.repository")
@EnableFeignClients
@EnableScheduling
@Import({JacksonConfig.class, InternalApiSecurityConfig.class})
public class NotificationServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(NotificationServiceApplication.class, args);
    }
}