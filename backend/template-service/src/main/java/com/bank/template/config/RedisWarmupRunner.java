package com.bank.template.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class RedisWarmupRunner implements CommandLineRunner {
    private static final Logger log = LoggerFactory.getLogger(RedisWarmupRunner.class);
    private final StringRedisTemplate redisTemplate;

    public RedisWarmupRunner(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void run(String... args) {
        try {
            redisTemplate.opsForValue().set("_warmup", "true");
            redisTemplate.delete("_warmup");
            log.info("Redis 连接预热成功 (Runner)");
        } catch (Exception e) {
            log.warn("Redis 预热失败（不影响业务）: {}", e.getMessage());
        }
    }
}