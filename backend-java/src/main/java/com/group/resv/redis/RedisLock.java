package com.group.resv.redis;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 简易分布式锁（SET NX + Lua 比较删除）。
 * 骨架期替代 Redisson，接口留了替换余地；value 用唯一串避免误删他人锁。
 */
@Component
public class RedisLock {

    private final StringRedisTemplate redis;
    private final DefaultRedisScript<Long> unlockScript;

    public RedisLock(StringRedisTemplate redis, DefaultRedisScript<Long> unlockScript) {
        this.redis = redis;
        this.unlockScript = unlockScript;
    }

    /** 加锁成功返回持有者标识，调用方在 finally 中 unlock；失败返回 null（不阻塞等待）。 */
    public String tryLock(String key, long ttlMillis) {
        String owner = UUID.randomUUID().toString();
        Boolean ok = redis.opsForValue().setIfAbsent(key, owner, Duration.ofMillis(ttlMillis));
        return Boolean.TRUE.equals(ok) ? owner : null;
    }

    public void unlock(String key, String owner) {
        if (owner == null) {
            return;
        }
        redis.execute(unlockScript, List.of(key), owner);
    }

    public boolean waitForLock(String key, long ttlMillis, long waitMillis) throws InterruptedException {
        long deadline = System.currentTimeMillis() + waitMillis;
        while (System.currentTimeMillis() < deadline) {
            if (tryLock(key, ttlMillis) != null) {
                return true;
            }
            TimeUnit.MILLISECONDS.sleep(20);
        }
        return false;
    }
}
