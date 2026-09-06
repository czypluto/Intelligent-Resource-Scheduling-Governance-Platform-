package com.group.resv.railway;

import com.group.resv.common.BizException;
import com.group.resv.railway.domain.TicketOrder;
import com.group.resv.railway.domain.Trip;
import com.group.resv.railway.repo.TicketOrderRepository;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Set;

/**
 * 购票确定性规则（独立于模型的"规则审核层"，集中可读、可单测）。
 * 所有写前置校验都收敛在这里，不在 Controller/Agent 里散落。
 */
@Component
public class TicketPolicy {

    private final TicketOrderRepository orderRepository;

    public TicketPolicy(TicketOrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    /** 车次须为当日或未来开行 */
    public void ensureNotPast(Trip trip) {
        if (trip.getTravelDate().isBefore(LocalDate.now())) {
            throw new BizException(400, "该车次开行日期已过，无法购买");
        }
    }

    /** 同人同车次同席别已有未取消订单则不放行（一人一票约束） */
    public void ensureOneTicket(Long userId, Long tripId, String seatClass) {
        boolean exists = orderRepository.existsByUserIdAndTripIdAndSeatClassAndStatusIn(
                userId, tripId, seatClass, Set.of(TicketOrder.PAID, TicketOrder.PENDING));
        if (exists) {
            throw new BizException(409, "您已购买该车次该席别车票，请勿重复购买");
        }
    }
}
