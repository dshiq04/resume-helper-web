package com.jobtrack.application.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class ResumeView {

    private Long id;
    private String name;
    private List<String> tags;
    private String fileName;
    private String fileType;
    private Long fileSize;
    private Boolean encrypted;
    private Boolean pinned;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
