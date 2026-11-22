package com.jobtrack.application.pojo.vo;

import com.jobtrack.application.pojo.po.ApplicationChange;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class TrashItemVO {

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
