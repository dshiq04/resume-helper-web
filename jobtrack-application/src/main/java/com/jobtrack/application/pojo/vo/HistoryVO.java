package com.jobtrack.application.pojo.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class HistoryVO {

    private List<ApplicationChangeVO> items;
    private List<String> missingStatuses;
    private int screeningDays;
    private LocalDateTime appliedAt;
}
