package com.group.resv.railway;

/** 购票业务 Redis key 规约。 */
public final class RailwayKeys {

    private RailwayKeys() {
    }

    /** 余票：某车次某席别剩余 */
    public static String stock(Long tripId, String seatClass) {
        return "rv:stock:" + tripId + ":" + seatClass;
    }

    public static String orderStream() {
        return "rv:stream:orders";
    }

    /** 一人一票活跃占位（SETNX 原子准入，杜绝并发重复下单的 TOCTOU）；取消/过期时删除。 */
    public static String active(Long userId, Long tripId, String seatClass) {
        return "rv:active:" + userId + ":" + tripId + ":" + seatClass;
    }
}
