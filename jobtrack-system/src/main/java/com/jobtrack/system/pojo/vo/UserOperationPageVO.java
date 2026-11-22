package com.jobtrack.system.pojo.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** 操作记录分页结果。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserOperationPageVO {

    private List<UserOperationVO> items;
    private long total;
    private long page;
    private long pageSize;
}
