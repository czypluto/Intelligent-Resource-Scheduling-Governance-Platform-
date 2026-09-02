package com.group.resv.seckill;

/**
 * 抢票/预约入参。该 POJO 同时用作 REST @RequestBody 与工具能力参数（Python/Agent 侧），
 * 保证 REST 与工具注册用的是同一份契约。
 */
public record SeckillRequest(Long resourceId, String requestId) {
}
