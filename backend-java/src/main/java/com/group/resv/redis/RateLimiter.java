package com.group.resv.redis;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Redis + Lua 令牌桶，全局限流。
 */
@Component
public class RateLimiter {

    private final StringRedisTemplate redis;
    private final DefaultRedisScript<Long> script;
    private final long capacity;
    private final long rate;
    private final long ttlMillis;

    public RateLimiter(StringRedisTemplate redis,
                       DefaultRedisScript<Long> tokenBucketScript,
                       @Value("${app.seckill.qps:1000}") long qps,
                       @Value("${app.seckill.burst:2000}") long burst) {
        this.redis = redis;
        this.script = tokenBucketScript;
        // 令牌按秒补充，rate=qps；ttl 留 5 分钟，无流量自动清桶
        this.rate = qps;
        this.capacity = Math.max(burst, qps);
        this.ttlMillis = 5 * 60_000L;
    }

    /** true=放行；false=触发限流。name 用于区分不同接口的桶。 */
    public boolean allow(String name) {
        String key = ResvKeys.rate(name);
        Long r = redis.execute(script, List.of(key),
                String.valueOf(capacity), String.valueOf(rate),
                String.valueOf(System.currentTimeMillis()), String.valueOf(ttlMillis));
        return r != null && r == 1L;
    }
}
