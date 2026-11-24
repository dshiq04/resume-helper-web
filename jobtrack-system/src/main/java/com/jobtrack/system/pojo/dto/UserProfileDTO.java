package com.jobtrack.system.pojo.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

/**
 * 个人资料保存请求，字段全部可选。
 */
@Data
public class UserProfileDTO {

    @Size(max = 64)
    private String realName;

    @Size(max = 16)
    @Pattern(regexp = "^(male|female|other)?$", message = "性别取值不正确")
    private String gender;

    private LocalDate birthday;

    @Size(max = 64)
    private String location;

    @Size(max = 64)
    private String hometown;

    @Size(max = 255)
    private String website;

    @Size(max = 128)
    private String github;

    @Size(max = 128)
    private String linkedin;

    @Size(max = 64)
    private String wechat;

    @Size(max = 128)
    private String targetPosition;

    @Size(max = 64)
    private String expectedSalary;

    @Size(max = 16)
    @Pattern(regexp = "^(onsite|hybrid|remote)?$", message = "工作方式取值不正确")
    private String workMode;

    @Size(max = 16)
    @Pattern(regexp = "^(looking|employed|student)?$", message = "求职状态取值不正确")
    private String jobStatus;

    private LocalDate availableDate;

    @Size(max = 128)
    private String school;

    @Size(max = 128)
    private String major;

    @Size(max = 32)
    private String degree;

    private Integer graduationYear;

    private Integer experienceYears;

    @Size(max = 128)
    private String currentCompany;

    @Size(max = 128)
    private String currentTitle;

    @Size(max = 1000)
    private String skills;

    @Size(max = 255)
    private String languages;

    @Size(max = 2000)
    private String bio;
}
