package com.bank.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * 内部服务鉴权开关：服务启动类 {@code @Import} 本类即可启用 {@link InternalApiAuthFilter}。
 */
@Configuration
@Import(InternalApiAuthFilter.Registration.class)
public class InternalApiSecurityConfig {
}
