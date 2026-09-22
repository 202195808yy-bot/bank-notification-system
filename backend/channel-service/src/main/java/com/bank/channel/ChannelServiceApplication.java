package com.bank.channel;

import com.bank.common.config.InternalApiSecurityConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication
@EntityScan("com.bank.common.entity")
@EnableJpaRepositories("com.bank.channel.repository")
@Import(InternalApiSecurityConfig.class)
public class ChannelServiceApplication {
	public static void main(String[] args) {
		SpringApplication.run(ChannelServiceApplication.class, args);
	}
}