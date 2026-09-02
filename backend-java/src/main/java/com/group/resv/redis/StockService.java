package com.group.resv.redis;

import com.group.resv.domain.ResvResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 库存预热与原子扣减。
 */
@Service
public class StockService {

    private final StringRedisTemplate redis;
    private final DefaultRedisScript<Long> stockDecrScript;

    public StockService(StringRedisTemplate redis, DefaultRedisScript<Long> stockDecrScript) {
        this.redis = redis;
        this.stockDecrScript = stockDecrScript;
    }

    /** 预热（覆盖写入）。预热失败仅返回 false，不抛错。 */
    public boolean prepare(ResvResource r) {
        String key = ResvKeys.stock(r.getId());
        try {
            redis.opsForValue().set(key, String.valueOf(r.getTotalStock()));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 原子扣减。
     * 返回 0 及以上 = 扣减后剩余；-1 库存不足；-2 未预热。
     */
    public long tryDecr(Long resourceId) {
        Long r = redis.execute(stockDecrScript, List.of(ResvKeys.stock(resourceId)));
        return r == null ? -2 : r;
    }

    /** 出票：按序号生成座位号 A1、A2… */
    public String assignSeat(Long resourceId) {
        Long n = redis.opsForValue().increment(ResvKeys.seatSeq(resourceId));
        long v = n == null ? 1 : n;
        return "A" + v;
    }
}
