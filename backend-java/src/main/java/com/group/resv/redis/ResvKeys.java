package com.group.resv.redis;

/**
 * Redis key 规约。统一前缀 resv:，避免与其他业务串扰。
 */
public final class ResvKeys {

    private static final String P = "resv:";

    private ResvKeys() {
    }

    /** 库存 */
    public static String stock(Long resourceId) {
        return P + "stock:" + resourceId;
    }

    /** 座位序号（出票用） */
    public static String seatSeq(Long resourceId) {
        return P + "seatseq:" + resourceId;
    }

    /** 一人一资源防重复（短窗口守卫，最终靠 DB 唯一索引） */
    public static String buyer(Long userId, Long resourceId) {
        return P + "buyer:" + userId + ":" + resourceId;
    }

    /** 幂等锁 */
    public static String idemLock(String requestId) {
        return P + "lock:req:" + requestId;
    }

    /** 限流桶 */
    public static String rate(String name) {
        return P + "rate:" + name;
    }

    public static String orderStream() {
        return P + "stream:orders";
    }
}
