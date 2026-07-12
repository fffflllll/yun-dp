package com.hmdp.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.hmdp.dto.Result;
import com.hmdp.dto.CreateMeetRoomRequest;
import com.hmdp.entity.MeetRoom;

/**
 * 聚会房间业务接口。
 */
public interface IMeetRoomService extends IService<MeetRoom> {

    /**
     * 当前登录用户创建聚会房间。
     *
     * @param request 创建房间参数
     * @return 房间ID、邀请码和房间状态
     */
    Result createRoom(CreateMeetRoomRequest request);

    /**
     * 当前登录用户通过邀请码加入聚会房间。
     *
     * @param inviteCode 六位邀请码
     * @return 操作结果
     */
    Result joinByInviteCode(String inviteCode);

    /**
     * 查询房间详情。
     *
     * 只有当前房间成员才能查询。
     *
     * @param roomId 房间ID
     * @return 房间信息和成员列表
     */
    Result getRoomDetail(Long roomId);
}