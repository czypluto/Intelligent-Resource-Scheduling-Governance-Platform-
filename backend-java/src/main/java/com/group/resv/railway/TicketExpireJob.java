package com.group.resv.railway;

import com.group.resv.railway.domain.TicketOrder;
import com.group.resv.railway.repo.TicketOrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 超时订单治理：把超过 10 分钟仍待支付的订单置为 EXPIRED 并回补余票。
 */
@Component
public class TicketExpireJob {

    private static final Logger log = LoggerFactory.getLogger(TicketExpireJob.class);

    private final TicketOrderRepository orderRepository;
    private final RailwayStockService stockService;
    private final StringRedisTemplate redis;

    public TicketExpireJob(TicketOrderRepository orderRepository, RailwayStockService stockService,
                           StringRedisTemplate redis) {
        this.orderRepository = orderRepository;
        this.stockService = stockService;
        this.redis = redis;
    }

    @Scheduled(fixedDelay = 60_000)
    public void expirePending() {
        List<TicketOrder> stale = orderRepository.findByStatusAndCreatedAtBefore(
                TicketOrder.PENDING, LocalDateTime.now().minusMinutes(10));
        for (TicketOrder o : stale) {
            o.setStatus(TicketOrder.EXPIRED);
            orderRepository.save(o);
            stockService.release(o.getTripId(), o.getSeatClass());
            redis.delete(RailwayKeys.active(o.getUserId(), o.getTripId(), o.getSeatClass()));
            log.info("订单 {} 超时未支付，已过期并回补余票、释放占位", o.getRequestId());
        }
    }
}
