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

    /** 一人一票活跃占位（车次级：每车次每人一票，不限席别）。取消/过期时删除。 */
    public static String active(Long userId, Long tripId) {
        return "rv:active:" + userId + ":" + tripId;
    }

    /** 座位号分配（每车次每席别独立原子递增，已分配的座不再给他人）。 */
    public static String seatSeq(Long tripId, String seatClass) {
        return "rv:seat:" + tripId + ":" + seatClass;
    }
}
