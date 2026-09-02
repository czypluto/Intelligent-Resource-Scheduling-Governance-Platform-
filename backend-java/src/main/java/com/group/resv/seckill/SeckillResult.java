package com.group.resv.seckill;

public record SeckillResult(boolean accepted, String status, String seatNo, String message) {
}
