package com.hmdp.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class AiMeetPlanSet {
    private List<AiMeetPlanOption> plans = new ArrayList<>();
}
