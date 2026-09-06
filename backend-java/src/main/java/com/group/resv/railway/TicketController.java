package com.group.resv.railway;

import com.group.resv.common.ApiResult;
import com.group.resv.railway.domain.TicketOrder;
import com.group.resv.security.SecurityUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
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
@Tag(name = "车票", description = "余票查询 / 购票 / 支付 / 退票 / 我的订单")
public class TicketController {

    private final TicketService ticketService;

    public TicketController(TicketService ticketService) {
        this.ticketService = ticketService;
    }

    @Operation(summary = "余票查询", description = "按起止站与日期查可用车次及席别余票")
    @GetMapping("/query")
    public ApiResult<List<Map<String, Object>>> query(
            @RequestParam Long from,
            @RequestParam Long to,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) String seatClass) {
        return ApiResult.ok(ticketService.query(from, to, date, seatClass));
    }

    @Operation(summary = "购票", description = "锁定库存并生成待支付订单（幂等，同人同车次同席别防重复）")
    @PostMapping("/buy")
    public ApiResult<Map<String, Object>> buy(@Valid @RequestBody TicketService.BuyRequest req) {
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
    public ApiResult<List<Map<String, Object>>> my(@RequestParam(defaultValue = "50") int size) {
        return ApiResult.ok(ticketService.myOrders(SecurityUtil.current().userId(), size));
    }

    @GetMapping("/orders/request/{requestId}")
    public ApiResult<Map<String, Object>> byRequest(@PathVariable String requestId) {
        return ApiResult.ok(ticketService.getByRequest(requestId, SecurityUtil.current().userId()));
    }
}
