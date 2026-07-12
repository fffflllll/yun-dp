package com.hmdp.controller;

import com.hmdp.dto.CreateMeetRoomRequest;
import com.hmdp.dto.JoinMeetRoomRequest;
import com.hmdp.dto.Result;
import com.hmdp.service.IMeetRoomService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/meet/rooms")
@RequiredArgsConstructor
public class MeetRoomController {

    @Resource
    private final IMeetRoomService meetRoomService;

    @PostMapping
    public Result createRoom(
            @Valid @RequestBody CreateMeetRoomRequest request) {

        return meetRoomService.createRoom(request);
    }

    @PostMapping("/join-by-code")
    public Result joinByCode(
            @Valid @RequestBody JoinMeetRoomRequest request) {

        return meetRoomService.joinByInviteCode(
                request.getInviteCode()
        );
    }

    @GetMapping("/{roomId}")
    public Result getRoomDetail(@PathVariable Long roomId) {
        return meetRoomService.getRoomDetail(roomId);
    }
}