package com.group.resv.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Redisson 分布式锁客户端。只用于加锁，不接管 Redis 连接池；
 * 缓存/Lua/Stream 仍走 Spring Data Redis（Lettuce），二者互不干扰。
 */
@Configuration
public class RedissonConfig {

    @Bean(destroyMethod = "shutdown")
    public RedissonClient redissonClient(
            @Value("${spring.data.redis.host:127.0.0.1}") String host,
            @Value("${spring.data.redis.port:6379}") int port) {
        Config c = new Config();
        c.useSingleServer().setAddress("redis://" + host + ":" + port);
        return Redisson.create(c);
    }
}
