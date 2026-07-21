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
     * 房主已锁定成员名单，等待所有成员确认偏好。
     */
    MEMBERS_LOCKED,

    /**
     * 已达到最低提交人数，可以发起规划。
     */
    READY_TO_PLAN,

    /**
     * 正在生成方案。
     */
    PLANNING,

    /**
     * 规划正在等待指定成员回答澄清问题。
     */
    WAITING_INPUT,

    /**
     * 已生成方案集，等待房主确认。
     */
    PLANS_READY,

    /**
     * 正在投票。
     */
    VOTING,

    /**
     * 已形成最终方案。
     */
    FINALIZED,

    /**
     * 规划结束但没有可行方案，或执行失败。
     */
    FAILED,

    /**
     * 房间已取消。
     */
    CANCELLED
}
