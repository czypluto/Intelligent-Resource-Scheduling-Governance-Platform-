package com.group.resv.railway;

import com.group.resv.railway.domain.TripClass;
import com.group.resv.railway.repo.TripClassRepository;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * 余票读写。规则：DB trip_class.totalSeats 是总席位，运行期余票只在 Redis 维护；
 * 未预热（key 缺失）时余票按全额显示。扣减用 Lua 保证原子，退票按余量回补。
 */
@Service
public class RailwayStockService {

    private final StringRedisTemplate redis;
    private final DefaultRedisScript<Long> stockDecrScript;
    private final TripClassRepository tripClassRepository;

    public RailwayStockService(StringRedisTemplate redis,
                               DefaultRedisScript<Long> stockDecrScript,
                               TripClassRepository tripClassRepository) {
        this.redis = redis;
        this.stockDecrScript = stockDecrScript;
        this.tripClassRepository = tripClassRepository;
    }

    /** 预热：把 DB 总席位写入 Redis（覆盖，等价“重新放票”）。 */
    public boolean preheat(Long tripId, String seatClass) {
        return tripClassRepository.findByTripIdAndSeatClass(tripId, seatClass)
                .map(tc -> {
                    try {
                        redis.opsForValue().set(RailwayKeys.stock(tripId, seatClass), String.valueOf(tc.getTotalSeats()));
                        return true;
                    } catch (Exception e) {
                        return false;
                    }
                })
                .orElse(false);
    }

    /** 当前余票；未预热返回总席位。 */
    public long remaining(Long tripId, String seatClass) {
        String v = redis.opsForValue().get(RailwayKeys.stock(tripId, seatClass));
        if (v != null) {
            return Long.parseLong(v);
        }
        Optional<TripClass> tc = tripClassRepository.findByTripIdAndSeatClass(tripId, seatClass);
        return tc.map(t -> (long) t.getTotalSeats()).orElse(0L);
    }

    /**
     * 原子扣减一张。返回 0 及以上=剩余；-1=无票；-2=未预热（需先 preheat 再扣）。
     */
    public long decr(Long tripId, String seatClass) {
        Long r = redis.execute(stockDecrScript, List.of(RailwayKeys.stock(tripId, seatClass)));
        return r == null ? -2 : r;
    }

    /** 退票回补一张，不超总席位。 */
    public void release(Long tripId, String seatClass) {
        String key = RailwayKeys.stock(tripId, seatClass);
        String v = redis.opsForValue().get(key);
        if (v == null) {
            return;
        }
        long cur = Long.parseLong(v);
        Optional<TripClass> tc = tripClassRepository.findByTripIdAndSeatClass(tripId, seatClass);
        long cap = tc.map(t -> (long) t.getTotalSeats()).orElse(cur);
        if (cur < cap) {
            redis.opsForValue().increment(key);
        }
    }
}
