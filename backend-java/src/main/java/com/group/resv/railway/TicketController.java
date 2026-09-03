package com.group.resv.railway;

import com.group.resv.common.ApiResult;
import com.group.resv.railway.domain.TicketOrder;
import com.group.resv.security.SecurityUtil;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 购票对外接口：余票查询、下单、支付、退票、我的订单。
 */
@RestController
@RequestMapping("/api/ticket")
public class TicketController {

    private final TicketService ticketService;

    public TicketController(TicketService ticketService) {
        this.ticketService = ticketService;
    }

    @GetMapping("/query")
    public ApiResult<List<Map<String, Object>>> query(
            @RequestParam Long from,
            @RequestParam Long to,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) String seatClass) {
        return ApiResult.ok(ticketService.query(from, to, date, seatClass));
    }

    @PostMapping("/buy")
    public ApiResult<Map<String, Object>> buy(@RequestBody TicketService.BuyRequest req) {
        return ApiResult.ok(ticketService.buy(SecurityUtil.current(), req));
    }

    @PostMapping("/orders/{requestId}/pay")
    public ApiResult<Map<String, Object>> pay(@PathVariable String requestId) {
        return ApiResult.ok(ticketService.pay(requestId, SecurityUtil.current().userId()));
    }

    @PostMapping("/orders/{requestId}/cancel")
    public ApiResult<Map<String, Object>> cancel(@PathVariable String requestId) {
        return ApiResult.ok(ticketService.cancel(requestId, SecurityUtil.current().userId()));
    }

    @GetMapping("/orders/my")
    public ApiResult<List<Map<String, Object>>> my() {
        return ApiResult.ok(ticketService.myOrders(SecurityUtil.current().userId()));
    }

    @GetMapping("/orders/request/{requestId}")
    public ApiResult<Map<String, Object>> byRequest(@PathVariable String requestId) {
        return ApiResult.ok(ticketService.getByRequest(requestId, SecurityUtil.current().userId()));
    }
}
