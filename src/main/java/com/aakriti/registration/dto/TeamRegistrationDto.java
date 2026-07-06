package com.aakriti.registration.dto;

import com.aakriti.registration.model.EventCategory;
import lombok.Data;

@Data
public class TeamRegistrationDto {
    private String teamName;
    private EventCategory category;
    private String eventName;
    private String leaderName;
    private String leaderEmail;
    private String leaderPhone;
    private String collegeName;
    private String yearOfStudy;
    private String memberNames;
}
