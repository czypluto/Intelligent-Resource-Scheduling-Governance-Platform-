package com.group.resv.railway;

import com.group.resv.common.BizException;
import com.group.resv.railway.domain.TicketOrder;
import com.group.resv.railway.domain.Trip;
import com.group.resv.railway.repo.TicketOrderRepository;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Set;

import static java.time.temporal.ChronoField.DAY_OF_MONTH;
import static java.time.temporal.ChronoField.MONTH_OF_YEAR;

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

    /** 儿童票硬前置：年满6周岁且未满14周岁，否则拒绝（确定性规则，不靠模型）。 */
    public void ensureChildEligible(Integer age) {
        if (age == null || age < 6 || age >= 14) {
            throw new BizException(400, "儿童票需年满6周岁且未满14周岁（当前年龄无效）");
        }
    }

    /** 学生票硬前置：须为学生身份、仅动车二等座、且乘车日在寒暑假窗口（7/1-8/31 或 1/15-2/28）。 */
    public void ensureStudentEligible(Boolean student, LocalDate travelDate, String seatClass) {
        if (!Boolean.TRUE.equals(student)) {
            throw new BizException(400, "学生票需全日制在校学生身份");
        }
        if (!"二等座".equals(seatClass)) {
            throw new BizException(400, "学生票仅限动车组二等座");
        }
        int m = travelDate.get(MONTH_OF_YEAR);
        int d = travelDate.get(DAY_OF_MONTH);
        boolean summer = m == 7 || (m == 8);
        boolean winter = (m == 1 && d >= 15) || (m == 2 && d <= 28);
        if (!summer && !winter) {
            throw new BizException(400, "学生票限寒暑假期间乘车（7/1-8/31 或 1/15-2/28）");
        }
    }

    /** 支付只允许从 PENDING 发生；PAID 视为幂等；CANCELLED/EXPIRED 一律拒绝。 */
    public void ensurePayable(TicketOrder o) {
        if (TicketOrder.PAID.equals(o.getStatus())) {
            return; // 幂等
        }
        if (TicketOrder.CANCELLED.equals(o.getStatus())) {
            throw new BizException(400, "订单已取消，无法支付");
        }
        if (TicketOrder.EXPIRED.equals(o.getStatus())) {
            throw new BizException(400, "订单已过期，无法支付");
        }
        if (!TicketOrder.PENDING.equals(o.getStatus())) {
            throw new BizException(400, "订单状态不允许支付");
        }
    }

    /** 退票只允许从 PENDING/PAID 发生；CANCELLED 视为幂等；EXPIRED 已回补余票，禁止二次退票回补。 */
    public void ensureCancellable(TicketOrder o) {
        if (TicketOrder.CANCELLED.equals(o.getStatus())) {
            return; // 幂等
        }
        if (TicketOrder.EXPIRED.equals(o.getStatus())) {
            throw new BizException(400, "订单已过期（余票已回补），无需再退票");
        }
        if (!TicketOrder.PENDING.equals(o.getStatus()) && !TicketOrder.PAID.equals(o.getStatus())) {
            throw new BizException(400, "订单状态不允许退票");
        }
    }
}
