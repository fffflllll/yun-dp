package com.hmdp.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hmdp.dto.Result;
import com.hmdp.dto.SendMeetMessageRequest;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.MeetMember;
import com.hmdp.entity.MeetMessage;
import com.hmdp.entity.User;
import com.hmdp.enums.MeetMemberStatus;
import com.hmdp.mapper.MeetMemberMapper;
import com.hmdp.mapper.MeetMessageMapper;
import com.hmdp.mapper.MeetRoomMapper;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IMeetMessageService;
import com.hmdp.utils.UserHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MeetMessageServiceImpl implements IMeetMessageService {

    private final MeetMessageMapper messageMapper;
    private final MeetMemberMapper memberMapper;
    private final MeetRoomMapper roomMapper;
    private final UserMapper userMapper;

    @Override
    public Result list(Long roomId, Long after) {
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            return Result.fail("用户未登录");
        }
        String accessError = requireMember(roomId, user.getId());
        if (accessError != null) {
            return Result.fail(accessError);
        }

        long sequence = after == null ? 0L : Math.max(0L, after);
        List<MeetMessage> messages = messageMapper.selectList(
                new LambdaQueryWrapper<MeetMessage>()
                        .eq(MeetMessage::getRoomId, roomId)
                        .gt(MeetMessage::getId, sequence)
                        .orderByAsc(MeetMessage::getId)
                        .last("limit 100"));
        return Result.ok(toViews(messages));
    }

    @Override
    public Result send(Long roomId, SendMeetMessageRequest request) {
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            return Result.fail("用户未登录");
        }
        String accessError = requireMember(roomId, user.getId());
        if (accessError != null) {
            return Result.fail(accessError);
        }
        if (request == null || request.getContent() == null
                || request.getContent().trim().isEmpty()) {
            return Result.fail("消息内容不能为空");
        }

        MeetMessage message = new MeetMessage();
        message.setRoomId(roomId);
        message.setUserId(user.getId());
        message.setContent(request.getContent().trim());
        message.setCreateTime(LocalDateTime.now());
        messageMapper.insert(message);

        return Result.ok(toView(message, userMapper.selectById(user.getId())));
    }

    private String requireMember(Long roomId, Long userId) {
        if (roomId == null || roomMapper.selectById(roomId) == null) {
            return "聚会房间不存在";
        }
        Long count = memberMapper.selectCount(
                new LambdaQueryWrapper<MeetMember>()
                        .eq(MeetMember::getRoomId, roomId)
                        .eq(MeetMember::getUserId, userId)
                        .eq(MeetMember::getStatus,
                                MeetMemberStatus.JOINED.name()));
        return count == 0 ? "你不是该房间成员" : null;
    }

    private List<Map<String, Object>> toViews(List<MeetMessage> messages) {
        if (messages.isEmpty()) {
            return List.of();
        }
        Map<Long, User> users = userMapper.selectBatchIds(
                        messages.stream().map(MeetMessage::getUserId).distinct().toList())
                .stream()
                .collect(Collectors.toMap(User::getId, Function.identity(),
                        (first, ignored) -> first));
        return messages.stream()
                .map(message -> toView(message, users.get(message.getUserId())))
                .toList();
    }

    private Map<String, Object> toView(MeetMessage message, User user) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", message.getId());
        view.put("userId", message.getUserId());
        view.put("userName", user == null ? "成员" : user.getNickName());
        view.put("icon", user == null ? null : user.getIcon());
        view.put("content", message.getContent());
        view.put("createTime", message.getCreateTime());
        return view;
    }
}
