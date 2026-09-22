package com.bank.template;

import com.bank.common.config.InternalApiSecurityConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication
@EntityScan("com.bank.common.entity")
@EnableJpaRepositories("com.bank.template.repository")
@Import(InternalApiSecurityConfig.class)
public class TemplateServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(TemplateServiceApplication.class, args);
    }
}