package com.bank.eventadapter;

import com.bank.common.config.InternalApiSecurityConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.context.annotation.Import;

@SpringBootApplication(exclude = {
		DataSourceAutoConfiguration.class,
		HibernateJpaAutoConfiguration.class
})
@Import(InternalApiSecurityConfig.class)
public class EventAdapterApplication {
	public static void main(String[] args) {
		SpringApplication.run(EventAdapterApplication.class, args);
	}
}