package com.jobtrack.application.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class ResumeUpdateRequest {

    @Size(max = 255, message = "简历名称不能超过 255 个字符")
    private String name;

    private List<String> tags;

    private Boolean pinned;
}
