package com.hmdp.service.impl;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hmdp.config.MeetAiProperties;
import com.hmdp.dto.ConfirmMeetPreferenceRequest;
import com.hmdp.dto.MeetPreferenceData;
import com.hmdp.dto.ParseMeetPreferenceRequest;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.MeetMember;
import com.hmdp.entity.MeetPreference;
import com.hmdp.entity.MeetRoom;
import com.hmdp.enums.MeetMemberStatus;
import com.hmdp.enums.MeetPreferenceStatus;
import com.hmdp.enums.MeetRoomStatus;
import com.hmdp.mapper.MeetMemberMapper;
import com.hmdp.mapper.MeetPreferenceMapper;
import com.hmdp.mapper.MeetRoomMapper;
import com.hmdp.service.IMeetPreferenceService;
import com.hmdp.service.MeetPreferenceAiService;
import com.hmdp.utils.UserHolder;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class MeetPreferenceServiceImpl implements IMeetPreferenceService {

    @Resource
    private MeetPreferenceMapper preferenceMapper;
    @Resource
    private MeetMemberMapper memberMapper;
    @Resource
    private MeetRoomMapper roomMapper;
    @Resource
    private MeetPreferenceAiService aiService;
    @Resource
    private MeetAiProperties aiProperties;
    @Resource
    private TransactionTemplate transactionTemplate;

    @Override
    public Result parse(Long roomId, ParseMeetPreferenceRequest request) {
        MemberContext context = requireEditableMember(roomId);
        if (context.error() != null) {
            return Result.fail(context.error());
        }

        MeetPreferenceAiService.PreferenceParseOutcome outcome =
                aiService.parse(request.getRawText().trim());
        return transactionTemplate.execute(status -> saveParsedPreference(
                roomId, request.getRawText().trim(), outcome));
    }

    private Result saveParsedPreference(
            Long roomId,
            String rawText,
            MeetPreferenceAiService.PreferenceParseOutcome outcome) {
        MemberContext context = requireEditableMemberForUpdate(roomId);
        if (context.error() != null) {
            return Result.fail(context.error());
        }
        LocalDateTime now = LocalDateTime.now();
        MeetPreference preference = find(roomId, context.userId());

        if (preference == null) {
            preference = new MeetPreference();
            preference.setRoomId(roomId);
            preference.setUserId(context.userId());
            preference.setVersion(0);
            preference.setCreateTime(now);
        } else {
            preference.setVersion(preference.getVersion() + 1);
        }

        preference.setRawText(rawText);
        preference.setDraftJson(JSONUtil.toJsonStr(outcome.preference()));
        preference.setConfirmedJson(null);
        preference.setConfirmedAt(null);
        preference.setStatus(MeetPreferenceStatus.DRAFT.name());
        preference.setParserVersion(aiProperties.getPromptVersion());
        preference.setUpdateTime(now);

        if (preference.getId() == null) {
            preferenceMapper.insert(preference);
        } else {
            preferenceMapper.updateById(preference);
        }

        MeetMember member = context.member();
        member.setPreferenceStatus(MeetPreferenceStatus.DRAFT.name());
        member.setUpdateTime(now);
        memberMapper.updateById(member);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("preference", outcome.preference());
        result.put("aiParsed", outcome.aiParsed());
        result.put("warning", outcome.warning());
        return Result.ok(result);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result confirm(Long roomId, ConfirmMeetPreferenceRequest request) {
        MemberContext context = requireEditableMemberForUpdate(roomId);
        if (context.error() != null) {
            return Result.fail(context.error());
        }

        MeetPreference preference = find(roomId, context.userId());
        if (preference == null) {
            return Result.fail("请先输入并解析偏好");
        }

        LocalDateTime now = LocalDateTime.now();
        preference.setConfirmedJson(
                JSONUtil.toJsonStr(request.getPreference())
        );
        preference.setStatus(MeetPreferenceStatus.CONFIRMED.name());
        preference.setConfirmedAt(now);
        preference.setVersion(preference.getVersion() + 1);
        preference.setUpdateTime(now);
        preferenceMapper.updateById(preference);

        MeetMember member = context.member();
        member.setPreferenceStatus(MeetPreferenceStatus.CONFIRMED.name());
        member.setUpdateTime(now);
        memberMapper.updateById(member);

        advanceRoomWhenAllConfirmed(context.room(), now);
        return Result.ok();
    }

    @Override
    public Result getMine(Long roomId) {
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            return Result.fail("用户未登录");
        }
        Long memberCount = memberMapper.selectCount(
                new LambdaQueryWrapper<MeetMember>()
                        .eq(MeetMember::getRoomId, roomId)
                        .eq(MeetMember::getUserId, user.getId())
                        .eq(MeetMember::getStatus,
                                MeetMemberStatus.JOINED.name()));
        if (memberCount == 0) {
            return Result.fail("你不是该房间成员");
        }
        MeetPreference preference = find(roomId, user.getId());
        if (preference == null) {
            return Result.ok();
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("rawText", preference.getRawText());
        result.put("status", preference.getStatus());
        result.put("draft", parseJson(preference.getDraftJson()));
        result.put("confirmed", parseJson(preference.getConfirmedJson()));
        result.put("confirmedAt", preference.getConfirmedAt());
        return Result.ok(result);
    }

    private MemberContext requireEditableMember(Long roomId) {
        return requireEditableMember(roomId, false);
    }

    private MemberContext requireEditableMemberForUpdate(Long roomId) {
        return requireEditableMember(roomId, true);
    }

    private MemberContext requireEditableMember(Long roomId, boolean lockRoom) {
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            return MemberContext.error("用户未登录");
        }

        LambdaQueryWrapper<MeetRoom> roomQuery =
                new LambdaQueryWrapper<MeetRoom>()
                        .eq(MeetRoom::getId, roomId);
        if (lockRoom) {
            roomQuery.last("FOR UPDATE");
        }
        MeetRoom room = roomMapper.selectOne(roomQuery);
        if (room == null) {
            return MemberContext.error("聚会房间不存在");
        }
        if (!MeetRoomStatus.COLLECTING_PREFERENCES.name()
                .equals(room.getStatus())
                && !MeetRoomStatus.MEMBERS_LOCKED.name()
                .equals(room.getStatus())) {
            return MemberContext.error("当前房间状态不能修改偏好");
        }

        MeetMember member = memberMapper.selectOne(
                new LambdaQueryWrapper<MeetMember>()
                        .eq(MeetMember::getRoomId, roomId)
                        .eq(MeetMember::getUserId, user.getId())
                        .eq(MeetMember::getStatus,
                                MeetMemberStatus.JOINED.name())
        );
        if (member == null) {
            return MemberContext.error("你不是该房间成员");
        }
        return new MemberContext(user.getId(), room, member, null);
    }

    private void advanceRoomWhenAllConfirmed(
            MeetRoom room, LocalDateTime now) {
        if (!MeetRoomStatus.MEMBERS_LOCKED.name()
                .equals(room.getStatus())) {
            return;
        }

        Long joined = memberMapper.selectCount(
                new LambdaQueryWrapper<MeetMember>()
                        .eq(MeetMember::getRoomId, room.getId())
                        .eq(MeetMember::getStatus,
                                MeetMemberStatus.JOINED.name())
        );
        Long confirmed = memberMapper.selectCount(
                new LambdaQueryWrapper<MeetMember>()
                        .eq(MeetMember::getRoomId, room.getId())
                        .eq(MeetMember::getStatus,
                                MeetMemberStatus.JOINED.name())
                        .eq(MeetMember::getPreferenceStatus,
                                MeetPreferenceStatus.CONFIRMED.name())
        );

        if (joined.equals(confirmed)) {
            room.setStatus(MeetRoomStatus.READY_TO_PLAN.name());
            room.setUpdateTime(now);
            room.setVersion(room.getVersion() + 1);
            roomMapper.updateById(room);
        }
    }

    private MeetPreference find(Long roomId, Long userId) {
        return preferenceMapper.selectOne(
                new LambdaQueryWrapper<MeetPreference>()
                        .eq(MeetPreference::getRoomId, roomId)
                        .eq(MeetPreference::getUserId, userId)
        );
    }

    private MeetPreferenceData parseJson(String json) {
        return json == null ? null
                : JSONUtil.toBean(json, MeetPreferenceData.class);
    }

    private record MemberContext(
            Long userId,
            MeetRoom room,
            MeetMember member,
            String error) {
        private static MemberContext error(String error) {
            return new MemberContext(null, null, null, error);
        }
    }
}
