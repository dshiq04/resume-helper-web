package com.jobtrack.application.pojo.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用户注销条件检查结果。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CancelStatusVO {

    /** 投递台账中剩余的记录数，0 表示已清除。 */
    private long applications;

    /** 回收站中剩余的记录数。 */
    private long trash;

    /** 简历库中剩余的简历数，0 表示已删除。 */
    private long resumes;

    /** 是否拥有普通用户以外的权限（如管理员），需要先放弃权限。 */
    private boolean elevatedPermission;
}
