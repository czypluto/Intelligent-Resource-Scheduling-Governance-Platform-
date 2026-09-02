package com.group.resv.seckill;

import com.group.resv.common.BizException;
import com.group.resv.domain.ReservationOrder;
import com.group.resv.domain.ResvResource;
import com.group.resv.permission.PermissionService;
import com.group.resv.redis.RateLimiter;
import com.group.resv.redis.ResvKeys;
import com.group.resv.redis.StockService;
import com.group.resv.repo.ReservationOrderRepository;
import com.group.resv.repo.ResvResourceRepository;
import com.group.resv.security.AuthUser;
import com.group.resv.security.SecurityUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;

/**
 * 抢票主流程：令牌桶限流 -> 权限确定性校验 -> request_id 幂等 -> Redis+Lua 扣库存
 * -> Redis Stream 异步落库。
 */
@Service
public class SeckillService {

    private static final Logger log = LoggerFactory.getLogger(SeckillService.class);

    private final RateLimiter rateLimiter;
    private final ResvResourceRepository resourceRepository;
    private final PermissionService permissionService;
    private final ReservationOrderRepository orderRepository;
    private final StockService stockService;
    private final StringRedisTemplate redis;

    public SeckillService(RateLimiter rateLimiter,
                          ResvResourceRepository resourceRepository,
                          PermissionService permissionService,
                          ReservationOrderRepository orderRepository,
                          StockService stockService,
                          StringRedisTemplate redis) {
        this.rateLimiter = rateLimiter;
        this.resourceRepository = resourceRepository;
        this.permissionService = permissionService;
        this.orderRepository = orderRepository;
        this.stockService = stockService;
        this.redis = redis;
    }

    public SeckillResult place(SeckillRequest req) {
        AuthUser user = SecurityUtil.current();

        // 1. 全局限流
        if (!rateLimiter.allow("seckill")) {
            throw new BizException(429, "系统繁忙，请稍后重试");
        }

        // 2. 资源存在性
        ResvResource res = resourceRepository.findById(req.resourceId())
                .orElseThrow(() -> new BizException(404, "资源不存在"));

        // 3. 权限二次校验（确定性规则，Java 侧闸门）
        permissionService.ensureAllowed(user, res.getType());

        // 4. request_id 幂等：重复请求直接返回第一次结果
        Optional<ReservationOrder> byReq = orderRepository.findByRequestId(req.requestId());
        if (byReq.isPresent()) {
            return toResult(byReq.get());
        }

        // 5. 一人一资源（DB 侧最终判定）
        Optional<ReservationOrder> byUser = orderRepository.findByUserIdAndResourceId(user.userId(), req.resourceId());
        if (byUser.isPresent()) {
            return toResult(byUser.get());
        }

        // 6. Redis 短窗口占位，挡并发窗口期的重复提交
        Boolean first = redis.opsForValue().setIfAbsent(
                ResvKeys.buyer(user.userId(), req.resourceId()), "1", Duration.ofDays(30));
        if (!Boolean.TRUE.equals(first)) {
            throw new BizException(400, "您已提交过该资源的预约");
        }

        // 7. Lua 原子扣库存
        long left = stockService.tryDecr(req.resourceId());
        if (left == -2) {
            // 未预热则就地预热一次再扣
            stockService.prepare(res);
            left = stockService.tryDecr(req.resourceId());
        }
        if (left == -1 || left == -2) {
            redis.delete(ResvKeys.buyer(user.userId(), req.resourceId()));
            throw new BizException(409, "库存不足，未抢到");
        }

        // 8. 出票 + 写 Stream，由异步消费者落库
        String seat = stockService.assignSeat(req.resourceId());
        String status = "SUCCESS";
        redis.opsForStream().add(ResvKeys.orderStream(), Map.of(
                "requestId", req.requestId(),
                "userId", String.valueOf(user.userId()),
                "resourceId", String.valueOf(req.resourceId()),
                "seatNo", seat,
                "status", status));

        log.info("抢票受理 user={} resource={} seat={} requestId={}",
                user.userId(), req.resourceId(), seat, req.requestId());
        return new SeckillResult(true, status, seat, "预约成功，座位号 " + seat);
    }

    private SeckillResult toResult(ReservationOrder order) {
        String msg = "SUCCESS".equals(order.getStatus())
                ? "预约成功，座位号 " + order.getSeatNo()
                : "您已预约过该资源，座位号 " + order.getSeatNo();
        return new SeckillResult(true, order.getStatus(), order.getSeatNo(), msg);
    }
}
