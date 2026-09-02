package com.group.resv.seckill;

import com.group.resv.common.ApiResult;
import com.group.resv.domain.ReservationOrder;
import com.group.resv.repo.ReservationOrderRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

/**
 * 按 request_id 查询订单（异步落库后，前端/Agent 轮询确认）。
 */
@RestController
@RequestMapping("/api/orders")
public class OrderQueryController {

    private final ReservationOrderRepository orderRepository;

    public OrderQueryController(ReservationOrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @GetMapping("/{requestId}")
    public ApiResult<Optional<ReservationOrder>> byRequestId(@PathVariable String requestId) {
        return ApiResult.ok(orderRepository.findByRequestId(requestId));
    }
}
