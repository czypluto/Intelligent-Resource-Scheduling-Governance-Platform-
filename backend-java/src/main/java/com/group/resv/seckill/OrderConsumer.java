package com.group.resv.seckill;

import com.group.resv.domain.ReservationOrder;
import com.group.resv.redis.ResvKeys;
import com.group.resv.repo.ReservationOrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

/**
 * Redis Stream 消费者：把抢票事件异步落库（削峰）。
 * 生产者已原子扣库存、生成座位号，这里只负责持久化与幂等去重。
 */
@Component
@Order(20)
public class OrderConsumer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(OrderConsumer.class);

    private final StringRedisTemplate redis;
    private final ReservationOrderRepository orderRepository;
    private final String orderKey;
    private final String group;
    private final String consumerName;

    public OrderConsumer(StringRedisTemplate redis,
                         ReservationOrderRepository orderRepository,
                         @Value("${app.stream.order-key}") String orderKey,
                         @Value("${app.stream.order-group}") String group,
                         @Value("${app.stream.order-consumer}") String consumerName) {
        this.redis = redis;
        this.orderRepository = orderRepository;
        this.orderKey = orderKey;
        this.group = group;
        this.consumerName = consumerName;
    }

    @Override
    public void run(ApplicationArguments args) {
        ensureGroup();
        Thread worker = new Thread(this::consume, "resv-order-consumer");
        worker.setDaemon(true);
        worker.start();
    }

    private void ensureGroup() {
        try {
            // 先保证 stream key 存在（插一条标记再删掉），再建消费组；组已存在则忽略。
            RecordId marker = redis.opsForStream().add(orderKey, Map.of("init", "1"));
            try {
                redis.opsForStream().createGroup(orderKey, group);
            } catch (Exception ignored) {
                // BUSYGROUP：消费组已存在
            }
            if (marker != null) {
                redis.opsForStream().delete(orderKey, marker);
            }
        } catch (Exception e) {
            log.warn("初始化订单消费组失败：{}", e.getMessage());
        }
    }

    private void consume() {
        Consumer consumer = Consumer.from(group, consumerName);
        StreamReadOptions options = StreamReadOptions.empty()
                .count(20)
                .block(Duration.ofMillis(1500));
        while (!Thread.currentThread().isInterrupted()) {
            try {
                List<MapRecord<String, Object, Object>> records = redis.opsForStream()
                        .read(consumer, options,
                                StreamOffset.create(ResvKeys.orderStream(), ReadOffset.lastConsumed()));
                if (records == null) {
                    continue;
                }
                for (MapRecord<String, Object, Object> r : records) {
                    handle(r);
                    RecordId id = r.getId();
                    redis.opsForStream().acknowledge(orderKey, group, id);
                    redis.opsForStream().delete(orderKey, id);
                }
            } catch (Exception e) {
                log.warn("订单消费异常，稍后重试：{}", e.getMessage());
                sleepQuietly(500);
            }
        }
    }

    private void handle(MapRecord<String, Object, Object> r) {
        Map<Object, Object> body = r.getValue();
        String requestId = str(body.get("requestId"));
        if (requestId == null || requestId.isEmpty()) {
            return;
        }
        if (orderRepository.findByRequestId(requestId).isPresent()) {
            return;
        }
        ReservationOrder order = new ReservationOrder();
        order.setRequestId(requestId);
        order.setUserId(Long.valueOf(str(body.get("userId"))));
        order.setResourceId(Long.valueOf(str(body.get("resourceId"))));
        order.setSeatNo(str(body.get("seatNo")));
        order.setStatus(str(body.get("status")));
        order.setCreatedAt(LocalDateTime.now());
        try {
            orderRepository.save(order);
        } catch (DataIntegrityViolationException e) {
            // 并发重复落库，容忍
        }
    }

    private String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private void sleepQuietly(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
