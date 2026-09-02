package com.group.resv.seckill;

import com.group.resv.common.ApiResult;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 抢票 REST 入口（Python/Agent 经此调用）。SeckillRequest 同时是工具参数契约。
 */
@RestController
@RequestMapping("/api/seckill")
public class SeckillController {

    private final SeckillService seckillService;

    public SeckillController(SeckillService seckillService) {
        this.seckillService = seckillService;
    }

    @PostMapping
    public ApiResult<SeckillResult> place(@RequestBody SeckillRequest request) {
        return ApiResult.ok(seckillService.place(request));
    }
}
