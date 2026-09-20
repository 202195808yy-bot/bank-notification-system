package com.bank.notification.config;

import com.bank.common.config.InternalApiAuthFilter;
import feign.RequestInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestTemplate;

import java.util.List;

/**
 * 服务间调用不经过网关，因此必须自己带上内部令牌，
 * 否则 customer-service / template-service 的 {@link InternalApiAuthFilter} 会直接 401。
 */
@Configuration
public class InternalClientConfig {

    @Bean
    public RequestInterceptor internalTokenFeignInterceptor(
            @Value("${internal.api.token:}") String internalApiToken) {
        return template -> template.header(InternalApiAuthFilter.HEADER, internalApiToken);
    }

    @Bean
    public RestTemplate restTemplate(@Value("${internal.api.token:}") String internalApiToken) {
        RestTemplate restTemplate = new RestTemplate();
        restTemplate.setInterceptors(List.of((request, body, execution) -> {
            request.getHeaders().add(InternalApiAuthFilter.HEADER, internalApiToken);
            return execution.execute(request, body);
        }));
        return restTemplate;
    }
}
