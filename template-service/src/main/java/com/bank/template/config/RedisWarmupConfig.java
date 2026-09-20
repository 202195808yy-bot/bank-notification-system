package com.bank.template.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration
public class RedisWarmupConfig {

    private static final Logger log = LoggerFactory.getLogger(RedisWarmupConfig.class);

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Bean
    CommandLineRunner warmupRedis() {
        return args -> {
            // 1. 使用原生客户端（保证底层 Socket 建立）
            // 注意：之前的 RedisClient 方式如果已经删掉，可以保留，否则这里直接使用 redisTemplate 预热即可。
            // 2. 使用 StringRedisTemplate 执行一次读写，激活连接池
            try {
                redisTemplate.opsForValue().set("_warmup", "ok");
                String value = redisTemplate.opsForValue().get("_warmup");
                redisTemplate.delete("_warmup");
                log.info("StringRedisTemplate 预热成功, 值 = {}", value);
            } catch (Exception e) {
                log.warn("StringRedisTemplate 预热失败: {}", e.getMessage());
            }
        };
    }
}