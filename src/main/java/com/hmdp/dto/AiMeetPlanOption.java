package com.hmdp.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class AiMeetPlanOption {
    private Long shopId;
    private String suggestedTime;
    private String meetingPoint;
    private String reasoning;
    private List<String> satisfiedPreferences = new ArrayList<>();
    private List<String> tradeoffs = new ArrayList<>();
}
