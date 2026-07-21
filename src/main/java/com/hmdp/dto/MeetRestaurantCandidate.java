package com.hmdp.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MeetRestaurantCandidate {
    private Long shopId;
    private String name;
    private String cuisine;
    private String area;
    private String address;
    private Long avgPrice;
    private Integer score;
    private String openHours;
    private Integer spicyLevel;
    private Double distanceMeters;
    private Double groupScore;
}
