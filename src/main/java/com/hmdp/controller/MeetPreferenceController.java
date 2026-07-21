package com.hmdp.controller;

import com.hmdp.dto.ConfirmMeetPreferenceRequest;
import com.hmdp.dto.ParseMeetPreferenceRequest;
import com.hmdp.dto.Result;
import com.hmdp.service.IMeetPreferenceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/meet/rooms/{roomId}/preferences")
@RequiredArgsConstructor
public class MeetPreferenceController {

    private final IMeetPreferenceService preferenceService;

    @PostMapping("/parse")
    public Result parse(
            @PathVariable Long roomId,
            @Valid @RequestBody ParseMeetPreferenceRequest request) {
        return preferenceService.parse(roomId, request);
    }

    @PutMapping("/confirm")
    public Result confirm(
            @PathVariable Long roomId,
            @Valid @RequestBody ConfirmMeetPreferenceRequest request) {
        return preferenceService.confirm(roomId, request);
    }

    @GetMapping("/me")
    public Result getMine(@PathVariable Long roomId) {
        return preferenceService.getMine(roomId);
    }
}
