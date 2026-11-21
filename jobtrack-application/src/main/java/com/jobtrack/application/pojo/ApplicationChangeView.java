package com.jobtrack.application.pojo;

import com.jobtrack.application.pojo.ApplicationChange;
import lombok.Data;

/**
 * 时间线节点，附带与上一阶段的间隔和在该状态停留的天数。
 */
@Data
public class ApplicationChangeView {

    private Long id;
    private Long applicationId;
    private String status;
    private String changedAt;
    private Integer daysSincePrevious;
    private Integer daysInStatus;

    public static ApplicationChangeView of(ApplicationChange change) {
        ApplicationChangeView view = new ApplicationChangeView();
        view.setId(change.getId());
        view.setApplicationId(change.getApplicationId());
        view.setStatus(change.getStatus());
        view.setChangedAt(change.getChangedAt() == null ? null : change.getChangedAt().toString());
        return view;
    }
}
