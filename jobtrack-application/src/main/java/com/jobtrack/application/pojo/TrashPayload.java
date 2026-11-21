package com.jobtrack.application.pojo;

import com.jobtrack.application.pojo.ApplicationChange;
import com.jobtrack.application.pojo.ApplicationRecord;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 回收站快照结构。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TrashPayload {

    private ApplicationRecord application;
    private List<ApplicationChange> history;
}
