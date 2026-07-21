package com.hmdp.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class AiMeetClarification {
    private Long targetUserId;
    private String constraintKey;
    private String question;
    private List<String> options = new ArrayList<>();
}
