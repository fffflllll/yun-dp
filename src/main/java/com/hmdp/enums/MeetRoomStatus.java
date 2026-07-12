package com.hmdp.enums;
/**
 * 聚会房间状态。
 */
public enum MeetRoomStatus {

    /**
     * 正在收集成员偏好。
     */
    COLLECTING_PREFERENCES,

    /**
     * 已达到最低提交人数，可以发起规划。
     */
    READY_TO_PLAN,

    /**
     * 正在生成方案。
     */
    PLANNING,

    /**
     * 正在投票。
     */
    VOTING,

    /**
     * 已形成最终方案。
     */
    FINALIZED,

    /**
     * 房间已取消。
     */
    CANCELLED
}