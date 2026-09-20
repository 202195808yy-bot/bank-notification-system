package com.bank.customer;

import com.bank.common.config.InternalApiSecurityConfig;
import com.bank.common.config.JacksonConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication
@EntityScan("com.bank.common.entity")
@EnableJpaRepositories("com.bank.customer.repository")
@Import({JacksonConfig.class, InternalApiSecurityConfig.class})
public class CustomerServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(CustomerServiceApplication.class, args);
    }
}
