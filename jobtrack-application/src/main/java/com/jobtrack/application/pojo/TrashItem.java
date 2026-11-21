package com.jobtrack.application.pojo;

import com.jobtrack.application.pojo.ApplicationChange;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class TrashItem {

    private Long id;
    private Long originalId;
    private String companyName;
    private String category;
    private String status;
    private String link;
    private Boolean pinned;
    private LocalDateTime appliedAt;
    private LocalDateTime deletedAt;
    private List<ApplicationChange> history;
}
