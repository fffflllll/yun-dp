package com.hmdp.controller;

import com.hmdp.dto.Result;
import com.hmdp.dto.SendMeetMessageRequest;
import com.hmdp.service.IMeetMessageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/meet/rooms/{roomId}/messages")
@RequiredArgsConstructor
public class MeetMessageController {

    private final IMeetMessageService messageService;

    @GetMapping
    public Result list(
            @PathVariable Long roomId,
            @RequestParam(value = "after", required = false) Long after) {
        return messageService.list(roomId, after);
    }

    @PostMapping
    public Result send(
            @PathVariable Long roomId,
            @Valid @RequestBody SendMeetMessageRequest request) {
        return messageService.send(roomId, request);
    }
}
