package com.hmdp.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.hmdp.dto.CreateMeetRoomRequest;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.MeetMember;
import com.hmdp.entity.MeetRoom;
import com.hmdp.entity.User;
import com.hmdp.enums.MeetMemberRole;
import com.hmdp.enums.MeetMemberStatus;
import com.hmdp.enums.MeetPreferenceStatus;
import com.hmdp.enums.MeetRoomStatus;
import com.hmdp.mapper.MeetMemberMapper;
import com.hmdp.mapper.MeetPlanRunMapper;
import com.hmdp.mapper.MeetRoomMapper;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IMeetRoomService;
import com.hmdp.vo.CreateMeetRoomResponse;
import com.hmdp.vo.MeetMemberVO;
import com.hmdp.vo.MeetRoomDetailVO;
import com.hmdp.vo.MeetRoomSummaryVO;
import com.hmdp.utils.UserHolder;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 聚会房间业务实现。
 */
@Service
public class MeetRoomServiceImpl
        extends ServiceImpl<MeetRoomMapper, MeetRoom>
        implements IMeetRoomService {

    /**
     * 为避免用户混淆，不使用数字0、1和字母O、I。
     */
    private static final String INVITE_CODE_CHARACTERS =
            "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private static final int INVITE_CODE_LENGTH = 6;

    private static final int MAX_INVITE_CODE_ATTEMPTS = 20;

    private static final SecureRandom RANDOM = new SecureRandom();

    @Resource
    private MeetMemberMapper meetMemberMapper;

    @Resource
    private UserMapper userMapper;

    @Resource
    private MeetPlanRunMapper meetPlanRunMapper;

    /**
     * 创建房间。
     *
     * 这个方法会完成两次数据库写入：
     *
     * 1. 插入房间；
     * 2. 将房主插入成员表。
     *
     * 因此必须使用事务。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result createRoom(CreateMeetRoomRequest request) {
        Long currentUserId = getCurrentUserId();

        if (currentUserId == null) {
            return Result.fail("用户未登录");
        }

        /*
         * 不完全依赖Controller的@Valid。
         * Service也做一次关键业务校验，避免以后其他业务直接调用Service时绕过校验。
         */
        String validationError = validateCreateRoomRequest(request);

        if (validationError != null) {
            return Result.fail(validationError);
        }

        int searchRadiusMeter =
                request.getSearchRadiusMeter() == null
                        ? 5000
                        : request.getSearchRadiusMeter();

        int maxMembers =
                request.getMaxMembers() == null
                        ? 6
                        : request.getMaxMembers();

        LocalDateTime now = LocalDateTime.now();

        /*
         * 第一步：构造并保存房间。
         */
        MeetRoom room = new MeetRoom();

        room.setCreatorId(currentUserId);
        room.setTitle(request.getTitle().trim());
        room.setInviteCode(generateUniqueInviteCode());
        room.setStatus(
                MeetRoomStatus.COLLECTING_PREFERENCES.name()
        );

        room.setCenterX(request.getCenterX());
        room.setCenterY(request.getCenterY());
        room.setSearchRadiusMeter(searchRadiusMeter);

        room.setMaxMembers(maxMembers);
        room.setVersion(0);

        room.setCreateTime(now);
        room.setUpdateTime(now);

        boolean roomSaved = save(room);

        if (!roomSaved || room.getId() == null) {
            /*
             * 这是数据库执行异常，不是正常的业务条件不满足。
             * 必须抛出异常，使@Transactional回滚事务。
             */
            throw new RuntimeException("聚会房间创建失败");
        }

        /*
         * 第二步：将创建者自动加入成员表，并设置为房主。
         */
        MeetMember owner = new MeetMember();

        owner.setRoomId(room.getId());
        owner.setUserId(currentUserId);
        owner.setRole(MeetMemberRole.OWNER.name());
        owner.setStatus(MeetMemberStatus.JOINED.name());
        owner.setPreferenceStatus(
                MeetPreferenceStatus.PENDING.name()
        );

        owner.setJoinTime(now);
        owner.setCreateTime(now);
        owner.setUpdateTime(now);

        int insertedMemberCount =
                meetMemberMapper.insert(owner);

        if (insertedMemberCount != 1) {
            /*
             * 此处抛出RuntimeException后：
             *
             * owner插入会失败；
             * 前面已经插入的room也会被事务回滚。
             */
            throw new RuntimeException("房主成员记录创建失败");
        }

        CreateMeetRoomResponse response =
                CreateMeetRoomResponse.builder()
                        .roomId(room.getId())
                        .inviteCode(room.getInviteCode())
                        .status(room.getStatus())
                        .build();

        return Result.ok(response);
    }

    /**
     * 通过邀请码加入房间。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result joinByInviteCode(String inviteCode) {
        Long currentUserId = getCurrentUserId();

        if (currentUserId == null) {
            return Result.fail("用户未登录");
        }

        if (inviteCode == null || inviteCode.trim().isEmpty()) {
            return Result.fail("邀请码不能为空");
        }

        /*
         * 邀请码统一转成大写。
         *
         * 用户输入 a8p4kq 和 A8P4KQ 时，都能查询到同一个房间。
         */
        String normalizedInviteCode =
                inviteCode.trim().toUpperCase(Locale.ROOT);

        MeetRoom room = lambdaQuery()
                .eq(
                        MeetRoom::getInviteCode,
                        normalizedInviteCode
                )
                .one();

        if (room == null) {
            return Result.fail("邀请码不存在");
        }

        if (!isRoomJoinable(room)) {
            return Result.fail("当前房间状态不允许继续加入");
        }

        /*
         * 查询这个用户是否曾经加入过该房间。
         *
         * 数据库中应存在：
         *
         * UNIQUE KEY uk_room_user(room_id, user_id)
         */
        MeetMember existingMember =
                meetMemberMapper.selectOne(
                        new LambdaQueryWrapper<MeetMember>()
                                .eq(
                                        MeetMember::getRoomId,
                                        room.getId()
                                )
                                .eq(
                                        MeetMember::getUserId,
                                        currentUserId
                                )
                );

        /*
         * 已经是有效成员时直接返回成功（含房间ID，便于前端跳转）。
         *
         * 这样即使前端重复点击加入按钮，也不会重复插入数据。
         */
        if (existingMember != null
                && MeetMemberStatus.JOINED.name()
                .equals(existingMember.getStatus())) {

            return Result.ok(room.getId());
        }

        /*
         * 统计当前房间内有效成员数量。
         */
        Long joinedMemberCount =
                meetMemberMapper.selectCount(
                        new LambdaQueryWrapper<MeetMember>()
                                .eq(
                                        MeetMember::getRoomId,
                                        room.getId()
                                )
                                .eq(
                                        MeetMember::getStatus,
                                        MeetMemberStatus.JOINED.name()
                                )
                );

        if (joinedMemberCount >= room.getMaxMembers()) {
            return Result.fail("房间人数已满");
        }

        LocalDateTime now = LocalDateTime.now();

        /*
         * 如果用户曾经加入过但后来退出，则恢复原成员记录。
         *
         * 当前MVP还没有退出接口，但这里提前兼容该情况。
         */
        if (existingMember != null) {
            existingMember.setStatus(
                    MeetMemberStatus.JOINED.name()
            );

            existingMember.setPreferenceStatus(
                    MeetPreferenceStatus.PENDING.name()
            );

            existingMember.setJoinTime(now);
            existingMember.setUpdateTime(now);

            int updatedCount =
                    meetMemberMapper.updateById(existingMember);

            if (updatedCount != 1) {
                throw new RuntimeException("恢复房间成员失败");
            }

            return Result.ok(room.getId());
        }

        /*
         * 第一次加入，创建新的成员记录。
         */
        MeetMember member = new MeetMember();

        member.setRoomId(room.getId());
        member.setUserId(currentUserId);
        member.setRole(MeetMemberRole.MEMBER.name());
        member.setStatus(MeetMemberStatus.JOINED.name());
        member.setPreferenceStatus(
                MeetPreferenceStatus.PENDING.name()
        );

        member.setJoinTime(now);
        member.setCreateTime(now);
        member.setUpdateTime(now);

        int insertedCount = meetMemberMapper.insert(member);

        if (insertedCount != 1) {
            throw new RuntimeException("加入聚会房间失败");
        }

        return Result.ok(room.getId());
    }

    /**
     * 查询房间详情。
     */
    @Override
    public Result getRoomDetail(Long roomId) {
        Long currentUserId = getCurrentUserId();

        if (currentUserId == null) {
            return Result.fail("用户未登录");
        }

        if (roomId == null || roomId <= 0) {
            return Result.fail("房间ID不正确");
        }

        MeetRoom room = getById(roomId);

        if (room == null) {
            return Result.fail("聚会房间不存在");
        }

        /*
         * 登录只说明用户是谁。
         *
         * 这里还需要检查用户是否有权访问这个具体房间。
         */
        boolean isMember =
                isJoinedRoomMember(roomId, currentUserId);

        if (!isMember) {
            return Result.fail("你不是该房间成员");
        }

        /*
         * 查询房间中所有状态为JOINED的成员。
         */
        List<MeetMember> memberEntities =
                meetMemberMapper.selectList(
                        new LambdaQueryWrapper<MeetMember>()
                                .eq(
                                        MeetMember::getRoomId,
                                        roomId
                                )
                                .eq(
                                        MeetMember::getStatus,
                                        MeetMemberStatus.JOINED.name()
                                )
                                .orderByAsc(
                                        MeetMember::getJoinTime
                                )
                );

        /*
         * 一次性批量查询所有成员的用户信息（昵称 + 头像），
         * 避免逐个成员查库导致的 N+1 问题。
         */
        Map<Long, User> userMap = loadUsers(memberEntities);

        List<MeetMemberVO> memberVOList =
                memberEntities.stream()
                        .map(m -> convertToMemberVO(m, userMap))
                        .toList();

        com.hmdp.entity.MeetPlanRun latestRun =
                meetPlanRunMapper.selectOne(
                        new LambdaQueryWrapper<com.hmdp.entity.MeetPlanRun>()
                                .eq(com.hmdp.entity.MeetPlanRun::getRoomId,
                                        roomId)
                                .orderByDesc(
                                        com.hmdp.entity.MeetPlanRun::getId)
                                .last("limit 1")
                );

        MeetRoomDetailVO roomDetail =
                MeetRoomDetailVO.builder()
                        .roomId(room.getId())
                        .creatorId(room.getCreatorId())
                        .title(room.getTitle())
                        .inviteCode(room.getInviteCode())
                        .status(room.getStatus())

                        .centerX(room.getCenterX())
                        .centerY(room.getCenterY())
                        .searchRadiusMeter(
                                room.getSearchRadiusMeter()
                        )

                        .maxMembers(room.getMaxMembers())
                        .createTime(room.getCreateTime())
                        .lockedAt(room.getLockedAt())
                        .confirmedProposalId(
                                room.getConfirmedProposalId()
                        )
                        .latestPlanRunId(
                                latestRun == null ? null : latestRun.getId()
                        )
                        .latestPlanRunStatus(
                                latestRun == null ? null : latestRun.getStatus()
                        )
                        .members(memberVOList)
                        .build();

        return Result.ok(roomDetail);
    }

    @Override
    public Result listMyRooms() {
        Long currentUserId = getCurrentUserId();
        if (currentUserId == null) {
            return Result.fail("用户未登录");
        }

        List<Long> roomIds = meetMemberMapper.selectList(
                        new LambdaQueryWrapper<MeetMember>()
                                .eq(MeetMember::getUserId, currentUserId)
                                .eq(MeetMember::getStatus,
                                        MeetMemberStatus.JOINED.name()))
                .stream()
                .map(MeetMember::getRoomId)
                .distinct()
                .toList();
        if (roomIds.isEmpty()) {
            return Result.ok(List.of());
        }

        List<MeetRoom> rooms = lambdaQuery()
                .in(MeetRoom::getId, roomIds)
                .orderByDesc(MeetRoom::getUpdateTime)
                .list();
        List<MeetMember> members = meetMemberMapper.selectList(
                new LambdaQueryWrapper<MeetMember>()
                        .in(MeetMember::getRoomId, roomIds)
                        .eq(MeetMember::getStatus,
                                MeetMemberStatus.JOINED.name())
                        .orderByAsc(MeetMember::getJoinTime));
        Map<Long, User> users = loadUsers(members);
        Map<Long, List<MeetMember>> membersByRoom = members.stream()
                .collect(Collectors.groupingBy(MeetMember::getRoomId));

        List<MeetRoomSummaryVO> summaries = rooms.stream()
                .map(room -> {
                    List<MeetMemberVO> roomMembers = membersByRoom
                            .getOrDefault(room.getId(), Collections.emptyList())
                            .stream()
                            .map(member -> convertToMemberVO(member, users))
                            .toList();
                    return MeetRoomSummaryVO.builder()
                            .roomId(room.getId())
                            .creatorId(room.getCreatorId())
                            .title(room.getTitle())
                            .inviteCode(room.getInviteCode())
                            .status(room.getStatus())
                            .maxMembers(room.getMaxMembers())
                            .memberCount(roomMembers.size())
                            .createTime(room.getCreateTime())
                            .members(roomMembers)
                            .build();
                })
                .toList();
        return Result.ok(summaries);
    }

    private Map<Long, User> loadUsers(List<MeetMember> members) {
        List<Long> userIds = members.stream()
                .map(MeetMember::getUserId)
                .distinct()
                .toList();
        if (userIds.isEmpty()) {
            return Map.of();
        }
        return userMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity(),
                        (first, ignored) -> first));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result lockMembers(Long roomId) {
        Long currentUserId = getCurrentUserId();
        MeetRoom room = getById(roomId);

        if (currentUserId == null) {
            return Result.fail("用户未登录");
        }
        if (room == null) {
            return Result.fail("聚会房间不存在");
        }
        if (!currentUserId.equals(room.getCreatorId())) {
            return Result.fail("只有房主可以锁定成员名单");
        }
        if (!MeetRoomStatus.COLLECTING_PREFERENCES.name()
                .equals(room.getStatus())) {
            return Result.fail("当前房间状态不能锁定成员名单");
        }

        Long joinedCount = meetMemberMapper.selectCount(
                new LambdaQueryWrapper<MeetMember>()
                        .eq(MeetMember::getRoomId, roomId)
                        .eq(MeetMember::getStatus,
                                MeetMemberStatus.JOINED.name())
        );
        if (joinedCount < 2) {
            return Result.fail("至少两名成员才能锁定名单");
        }

        LocalDateTime now = LocalDateTime.now();
        room.setStatus(MeetRoomStatus.MEMBERS_LOCKED.name());
        room.setLockedAt(now);
        room.setUpdateTime(now);
        room.setVersion(room.getVersion() + 1);
        updateById(room);
        return Result.ok();
    }

    /**
     * 获取当前登录用户ID。
     */
    private Long getCurrentUserId() {
        UserDTO currentUser = UserHolder.getUser();

        if (currentUser == null) {
            return null;
        }

        return currentUser.getId();
    }

    /**
     * 校验创建房间参数。
     *
     * 返回null表示校验成功；
     * 返回字符串表示具体错误信息。
     */
    private String validateCreateRoomRequest(
            CreateMeetRoomRequest request) {

        if (request == null) {
            return "创建房间参数不能为空";
        }

        if (request.getTitle() == null
                || request.getTitle().trim().isEmpty()) {

            return "聚会标题不能为空";
        }

        if (request.getTitle().trim().length() > 100) {
            return "聚会标题不能超过100个字符";
        }

        Double centerX = request.getCenterX();
        Double centerY = request.getCenterY();

        if (centerX == null
                || !Double.isFinite(centerX)
                || centerX < -180D
                || centerX > 180D) {

            return "经度必须在-180到180之间";
        }

        if (centerY == null
                || !Double.isFinite(centerY)
                || centerY < -90D
                || centerY > 90D) {

            return "纬度必须在-90到90之间";
        }

        int searchRadiusMeter =
                request.getSearchRadiusMeter() == null
                        ? 5000
                        : request.getSearchRadiusMeter();

        if (searchRadiusMeter < 500
                || searchRadiusMeter > 20000) {

            return "搜索半径必须在500到20000米之间";
        }

        int maxMembers =
                request.getMaxMembers() == null
                        ? 6
                        : request.getMaxMembers();

        if (maxMembers < 2 || maxMembers > 20) {
            return "房间人数必须在2到20人之间";
        }

        return null;
    }

    /**
     * 判断房间当前是否允许加入。
     *
     * MVP中只允许在收集偏好或等待规划阶段加入。
     */
    private boolean isRoomJoinable(MeetRoom room) {
        String status = room.getStatus();

        return room.getLockedAt() == null
                && MeetRoomStatus.COLLECTING_PREFERENCES
                .name()
                .equals(status);
    }

    /**
     * 判断用户是否为当前房间的有效成员。
     */
    private boolean isJoinedRoomMember(
            Long roomId,
            Long userId) {

        Long memberCount =
                meetMemberMapper.selectCount(
                        new LambdaQueryWrapper<MeetMember>()
                                .eq(
                                        MeetMember::getRoomId,
                                        roomId
                                )
                                .eq(
                                        MeetMember::getUserId,
                                        userId
                                )
                                .eq(
                                        MeetMember::getStatus,
                                        MeetMemberStatus.JOINED.name()
                                )
                );

        return memberCount > 0;
    }

    /**
     * 将数据库实体转换成接口返回对象。
     */
    private MeetMemberVO convertToMemberVO(
            MeetMember member,
            Map<Long, User> userMap) {

        User user = userMap.get(member.getUserId());

        return MeetMemberVO.builder()
                .userId(member.getUserId())
                .nickName(user != null ? user.getNickName() : null)
                .icon(user != null ? user.getIcon() : null)
                .role(member.getRole())
                .status(member.getStatus())
                .preferenceStatus(
                        member.getPreferenceStatus()
                )
                .joinTime(member.getJoinTime())
                .build();
    }

    /**
     * 生成数据库中尚未出现的邀请码。
     *
     * 数据库的唯一索引仍然是最终防线。
     */
    private String generateUniqueInviteCode() {
        for (int attempt = 0;
             attempt < MAX_INVITE_CODE_ATTEMPTS;
             attempt++) {

            String inviteCode = randomInviteCode();

            Long count = lambdaQuery()
                    .eq(
                            MeetRoom::getInviteCode,
                            inviteCode
                    )
                    .count();

            if (count == 0) {
                return inviteCode;
            }
        }

        throw new RuntimeException("生成唯一邀请码失败");
    }

    /**
     * 随机生成六位邀请码。
     */
    private String randomInviteCode() {
        StringBuilder builder =
                new StringBuilder(INVITE_CODE_LENGTH);

        for (int index = 0;
             index < INVITE_CODE_LENGTH;
             index++) {

            int randomIndex =
                    RANDOM.nextInt(
                            INVITE_CODE_CHARACTERS.length()
                    );

            builder.append(
                    INVITE_CODE_CHARACTERS.charAt(randomIndex)
            );
        }

        return builder.toString();
    }
}
