package com.jobtrack.application.pojo.vo;

import com.jobtrack.application.pojo.po.ApplicationChange;
import lombok.Data;

/**
 * 时间线节点，附带与上一阶段的间隔和在该状态停留的天数。
 */
@Data
public class ApplicationChangeVO {

    private Long id;
    private Long applicationId;
    private String status;
    private String changedAt;
    private Integer daysSincePrevious;
    private Integer daysInStatus;

    public static ApplicationChangeVO of(ApplicationChange change) {
        ApplicationChangeVO view = new ApplicationChangeVO();
        view.setId(change.getId());
        view.setApplicationId(change.getApplicationId());
        view.setStatus(change.getStatus());
        view.setChangedAt(change.getChangedAt() == null ? null : change.getChangedAt().toString());
        return view;
    }
}
