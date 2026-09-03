package com.group.resv.railway;

import com.group.resv.railway.domain.TicketOrder;
import com.group.resv.railway.repo.TicketOrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** Redis Stream 消费者：把购票事件异步落库（削峰），订单先落为待支付。 */
@Component
@Order(21)
public class TicketOrderConsumer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(TicketOrderConsumer.class);

    private final StringRedisTemplate redis;
    private final TicketOrderRepository orderRepository;
    private final String streamKey = RailwayKeys.orderStream();
    private final String group = "rv-order-g";
    private final String consumerName = "rv-order-c1";

    public TicketOrderConsumer(StringRedisTemplate redis, TicketOrderRepository orderRepository) {
        this.redis = redis;
        this.orderRepository = orderRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        ensureGroup();
        Thread worker = new Thread(this::consume, "rv-order-consumer");
        worker.setDaemon(true);
        worker.start();
    }

    private void ensureGroup() {
        try {
            RecordId marker = redis.opsForStream().add(streamKey, Map.of("init", "1"));
            try {
                redis.opsForStream().createGroup(streamKey, group);
            } catch (Exception ignored) {
                // 消费组已存在
            }
            if (marker != null) {
                redis.opsForStream().delete(streamKey, marker);
            }
        } catch (Exception e) {
            log.warn("初始化购票消费组失败：{}", e.getMessage());
        }
    }

    private void consume() {
        Consumer consumer = Consumer.from(group, consumerName);
        StreamReadOptions options = StreamReadOptions.empty().count(20).block(Duration.ofMillis(1500));
        while (!Thread.currentThread().isInterrupted()) {
            try {
                List<MapRecord<String, Object, Object>> records = redis.opsForStream()
                        .read(consumer, options,
                                StreamOffset.create(streamKey, ReadOffset.lastConsumed()));
                if (records == null) {
                    continue;
                }
                for (MapRecord<String, Object, Object> r : records) {
                    handle(r.getValue());
                    RecordId id = r.getId();
                    redis.opsForStream().acknowledge(streamKey, group, id);
                    redis.opsForStream().delete(streamKey, id);
                }
            } catch (Exception e) {
                log.warn("购票订单消费异常：{}", e.getMessage());
                sleepQuietly(500);
            }
        }
    }

    private void handle(Map<Object, Object> body) {
        String requestId = str(body.get("requestId"));
        if (requestId == null || requestId.isEmpty()) {
            return;
        }
        if (orderRepository.findByRequestId(requestId).isPresent()) {
            return;
        }
        TicketOrder o = new TicketOrder();
        o.setRequestId(requestId);
        o.setOrderNo(str(body.get("orderNo")));
        o.setUserId(Long.valueOf(str(body.get("userId"))));
        o.setTripId(Long.valueOf(str(body.get("tripId"))));
        o.setSeatClass(str(body.get("seatClass")));
        o.setFromStation(str(body.get("from")));
        o.setToStation(str(body.get("to")));
        o.setPassengerName(str(body.get("passengerName")));
        o.setPassengerId(str(body.get("passengerId")));
        o.setPriceCents(Long.parseLong(str(body.get("priceCents"))));
        o.setStatus(TicketOrder.PENDING);
        o.setCreatedAt(LocalDateTime.now());
        try {
            orderRepository.save(o);
        } catch (DataIntegrityViolationException e) {
            // 重复落库，容忍
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
