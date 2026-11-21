package com.jobtrack.system.pojo;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class UserProfileView {

    private Long userId;
    private String realName;
    private String gender;
    private LocalDate birthday;
    private String phone;
    private String email;
    private String location;
    private String hometown;
    private String website;
    private String github;
    private String linkedin;
    private String wechat;
    private String targetPosition;
    private String expectedSalary;
    private String workMode;
    private String jobStatus;
    private LocalDate availableDate;
    private String school;
    private String major;
    private String degree;
    private Integer graduationYear;
    private Integer experienceYears;
    private String currentCompany;
    private String currentTitle;
    private String skills;
    private String languages;
    private String bio;
    private String avatarUrl;
    private LocalDateTime updatedAt;
}
