package com.jobtrack.system.pojo.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AdminUserPageVO {

    private List<AdminUserVO> items;
    private long total;
    private int page;
    private int pageSize;
}
